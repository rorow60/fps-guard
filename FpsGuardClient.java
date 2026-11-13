package com.nicolas.fpsguard;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * FPS Guard : réglage dynamique de la qualité.
 * - FPS illimités et V-Sync coupée.
 * - Si les FPS passent sous 80 : baisse la distance d'affichage / simulation / entités.
 * - Si les FPS restent très au-dessus de 80 : remonte progressivement la qualité.
 */
public class FpsGuardClient implements ClientModInitializer {

	private static final int MIN_FPS = 80;
	private static final int RAISE_FPS = 160;       // marge avant de remonter la qualité
	private static final int SAMPLE_TICKS = 20;     // 1 mesure par seconde
	private static final int LOWER_AFTER = 2;       // mesures basses consécutives avant de baisser
	private static final int RAISE_AFTER = 10;      // mesures hautes consécutives avant de remonter
	private static final int COOLDOWN_SAMPLES = 4;  // pause après un changement (rechargement des chunks)

	private static final int MIN_VIEW = 4;
	private static final int MIN_SIM = 5;
	private static final double MIN_ENTITY = 0.5;

	private int tickCounter = 0;
	private int lowCount = 0;
	private int highCount = 0;
	private int cooldown = 0;

	private int targetView = -1;
	private int targetSim = -1;
	private double targetEntity = -1;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(MinecraftClient client) {
		if (client.world == null || client.options == null) {
			return;
		}
		GameOptions o = client.options;

		if (targetView < 0) {
			// Réglages choisis par le joueur = plafond de qualité.
			targetView = o.getViewDistance().getValue();
			targetSim = o.getSimulationDistance().getValue();
			targetEntity = o.getEntityDistanceScaling().getValue();
			o.getMaxFps().setValue(260);          // 260 = illimité
			o.getEnableVsync().setValue(false);
		}

		if (++tickCounter < SAMPLE_TICKS) {
			return;
		}
		tickCounter = 0;

		if (cooldown > 0) {
			cooldown--;
			return;
		}

		int fps = client.getCurrentFps();

		if (fps < MIN_FPS) {
			highCount = 0;
			if (++lowCount >= LOWER_AFTER) {
				lowCount = 0;
				if (lower(o)) {
					cooldown = COOLDOWN_SAMPLES;
				}
			}
		} else if (fps > RAISE_FPS) {
			lowCount = 0;
			if (++highCount >= RAISE_AFTER) {
				highCount = 0;
				if (raise(o)) {
					cooldown = COOLDOWN_SAMPLES;
				}
			}
		} else {
			lowCount = 0;
			highCount = 0;
		}
	}

	private boolean lower(GameOptions o) {
		int sim = o.getSimulationDistance().getValue();
		if (sim > MIN_SIM) {
			o.getSimulationDistance().setValue(sim - 1);
			return true;
		}
		double ent = o.getEntityDistanceScaling().getValue();
		if (ent > MIN_ENTITY) {
			o.getEntityDistanceScaling().setValue(Math.max(MIN_ENTITY, ent - 0.25));
			return true;
		}
		int view = o.getViewDistance().getValue();
		if (view > MIN_VIEW) {
			o.getViewDistance().setValue(view - 1);
			return true;
		}
		return false;
	}

	private boolean raise(GameOptions o) {
		int view = o.getViewDistance().getValue();
		if (view < targetView) {
			o.getViewDistance().setValue(view + 1);
			return true;
		}
		double ent = o.getEntityDistanceScaling().getValue();
		if (ent < targetEntity) {
			o.getEntityDistanceScaling().setValue(Math.min(targetEntity, ent + 0.25));
			return true;
		}
		int sim = o.getSimulationDistance().getValue();
		if (sim < targetSim) {
			o.getSimulationDistance().setValue(sim + 1);
			return true;
		}
		return false;
	}
}
