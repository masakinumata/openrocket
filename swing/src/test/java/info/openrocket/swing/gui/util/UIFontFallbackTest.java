package info.openrocket.swing.gui.util;

import java.awt.Font;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UIFontFallbackTest {
	@Test
	@ResourceLock(Resources.LOCALE)
	void japaneseLabelsUseAvailableSystemGlyphsInEveryUiWeight() {
		String labels = "ファイル 解析 設計診断 閉じる";
		Font systemFont = new Font(Font.DIALOG, Font.PLAIN, 13);
		assumeTrue(systemFont.canDisplayUpTo(labels) == -1, "Japanese system glyphs required");

		Locale originalLocale = Locale.getDefault();
		try {
			Locale.setDefault(Locale.JAPANESE);
			for (String style : List.of(GUIUtil.UI_FONT_STYLE_LIGHT, GUIUtil.UI_FONT_STYLE_REGULAR,
					GUIUtil.UI_FONT_STYLE_MEDIUM, GUIUtil.UI_FONT_STYLE_BOLD)) {
				Font font = GUIUtil.createUIFont(style, 13, 0);
				assertEquals(-1, font.canDisplayUpTo(labels), style);
			}
		} finally {
			Locale.setDefault(originalLocale);
		}
	}
}
