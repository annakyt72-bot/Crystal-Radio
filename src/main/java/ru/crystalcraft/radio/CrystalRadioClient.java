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
                Minecraft.getInstance().execute(() ->
                    Minecraft.getInstance().setScreen(new RadioScreen())
                );
                return 1;
            }))
        );
    }
}
