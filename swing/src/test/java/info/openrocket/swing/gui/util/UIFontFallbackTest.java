package info.openrocket.swing.gui.util;

import java.awt.Font;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UIFontFallbackTest {
	@Test
	void japaneseLabelsUseAvailableSystemGlyphsInEveryUiWeight() {
		String labels = "ファイル 解析 設計診断 閉じる";
		Font systemFont = new Font(Font.DIALOG, Font.PLAIN, 13);
		assumeTrue(systemFont.canDisplayUpTo(labels) == -1, "Japanese system glyphs required");

		for (String style : List.of(GUIUtil.UI_FONT_STYLE_LIGHT, GUIUtil.UI_FONT_STYLE_REGULAR,
				GUIUtil.UI_FONT_STYLE_MEDIUM, GUIUtil.UI_FONT_STYLE_BOLD)) {
			Font font = GUIUtil.createUIFont(style, 13, 0);
			assertEquals(-1, font.canDisplayUpTo(labels), style);
		}
	}
}
