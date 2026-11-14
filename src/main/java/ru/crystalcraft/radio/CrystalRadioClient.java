package ru.crystalcraft.radio;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class CrystalRadioClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("radio").executes(context -> {
                Minecraft client = Minecraft.getInstance();

                // Client commands may be invoked from a command callback.
                // Always switch screens on the Minecraft client thread.
                client.execute(() -> {
                    try {
                        client.setScreen(new RadioScreen());
                    } catch (RuntimeException exception) {
                        System.err.println("[Crystal Radio] Failed to open the radio screen.");
                        exception.printStackTrace(System.err);
                    }
                });

                return 1;
            }))
        );
    }
}
