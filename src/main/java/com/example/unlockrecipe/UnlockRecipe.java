package com.example.unlockrecipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server side mod: whenever a player joins the world, run {@code /recipe give <player> *} for them.
 *
 * <p>Nothing is recorded about previous visits: the command is executed on every join, exactly as
 * asked. Re-running it is harmless - vanilla simply reports that no new recipes were given when the
 * player already knows all of them.
 *
 * <p>The {@code main} entrypoint is used on purpose. Fabric Loader only invokes the {@code server}
 * entrypoint ({@link net.fabricmc.api.DedicatedServerModInitializer}) when the game itself is a
 * dedicated server; the integrated server of singleplayer / LAN runs inside a client, where only
 * {@code main} and {@code client} are invoked. Registering from {@code main} therefore covers a
 * dedicated server and the integrated server with one code path.
 */
public final class UnlockRecipe implements ModInitializer {
	public static final String MOD_ID = "unlockrecipe";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** {@code *} is the vanilla wildcard of {@code /recipe give}: every recipe the server knows. */
	private static final String ALL_RECIPES = "*";

	/**
	 * Players seen joining, whose recipe command has not run yet. A plain set is enough: it is only
	 * ever touched from the server thread.
	 */
	private static final Set<UUID> PENDING = new LinkedHashSet<>();

	@Override
	public void onInitialize() {
		// Fabric fires this event at the head of PlayerList#placeNewPlayer, i.e. before the joining
		// player has been put into the player list. A name selector such as "Steve" cannot be resolved
		// at that point and the command would fail with "player not found" (and MinecraftServer#execute
		// does not help: called from the server thread it runs the task inline). So only remember the
		// player here ...
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PENDING.add(handler.player.getUUID()));

		// ... and run the command at the end of the tick, once the join is complete.
		ServerTickEvents.END_SERVER_TICK.register(UnlockRecipe::runPending);

		LOGGER.info("unlockrecipe ready: every joining player gets /recipe give <player> {}", ALL_RECIPES);
	}

	private static void runPending(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}

		List<UUID> joining = new ArrayList<>(PENDING);
		PENDING.clear();

		for (UUID uuid : joining) {
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);

			// null when the player already left again, e.g. a connection that dropped during login.
			if (player != null) {
				giveAllRecipes(server, player);
			}
		}
	}

	private static void giveAllRecipes(MinecraftServer server, ServerPlayer player) {
		String name = player.getScoreboardName();
		RecipeManager recipes = server.getRecipeManager();

		int knownBefore = countKnown(player, recipes);
		String command = "recipe give " + name + " " + ALL_RECIPES;

		try {
			// The console source has permission level 4 and works with cheats disabled; the command
			// reports its own result (success or "no new recipes") to the server log.
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
		} catch (Throwable t) {
			LOGGER.error("{} joined, but /{} could not be executed", name, command, t);
			return;
		}

		int knownAfter = countKnown(player, recipes);
		LOGGER.info("{} joined -> /{} : {} -> {} of {} recipes unlocked for the player",
				name, command, knownBefore, knownAfter, recipes.getRecipes().size());
	}

	/**
	 * The recipe book is a set of resource keys, so the known recipes have to be counted rather
	 * than read from a size. Used only to log the effect of the command.
	 */
	private static int countKnown(ServerPlayer player, RecipeManager recipes) {
		int known = 0;

		for (RecipeHolder<?> recipe : recipes.getRecipes()) {
			if (player.getRecipeBook().contains(recipe.id())) {
				known++;
			}
		}

		return known;
	}
}
