package com.flyvisual.module.impl;

import com.flyvisual.gui.FlyDeathScreen;
import com.flyvisual.module.DoubleSetting;
import com.flyvisual.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;

/**
 * Экран смерти FlyVisual: полностью заменяет ванильный экран (панель с тем, кто тебя убил,
 * координатами и измерением, свои кнопки в стиле мода). Координаты можно записать в чат (видишь только ты).
 * Подмена экрана происходит в MinecraftClientMixin, причина смерти берётся в DeathScreenMixin.
 */
public class DeathScreenModule extends Module {
	private final DoubleSetting vignette = addToggle("Red vignette", true);
	private final DoubleSetting coordsInChat = addToggle("Write coords in chat", true);

	private String message = "";
	private boolean hardcore;
	private boolean announced;

	public DeathScreenModule() {
		super("Death Screen", "Who killed you and where");
	}

	@Override
	protected boolean enabledByDefault() {
		return true;
	}

	/** Запоминает текст причины смерти и режим хардкор, которые игра передаёт в экран смерти. */
	public void setMessage(Text text, boolean hardcore) {
		this.message = text == null ? "" : text.getString();
		this.hardcore = hardcore;
		this.announced = false;
	}

	public String getMessage() {
		return message;
	}

	public boolean isHardcore() {
		return hardcore;
	}

	public boolean showVignette() {
		return vignette.getBool();
	}

	/** True один раз за смерть, если нужно написать координаты в чат. */
	public boolean takeChatAnnouncement() {
		if (!coordsInChat.getBool() || announced) return false;
		announced = true;
		return true;
	}

	public FlyDeathScreen createScreen() {
		return new FlyDeathScreen(this);
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.player != null && client.player.isAlive()) {
			announced = false;
		}
		// Запасной путь: если игра открыла ванильный экран смерти в обход setScreen(экран), заменяем его
		if (client.currentScreen != null && client.currentScreen.getClass() == DeathScreen.class) {
			client.setScreen(createScreen());
		}
	}
}
