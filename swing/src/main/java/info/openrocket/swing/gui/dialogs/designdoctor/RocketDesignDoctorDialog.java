package info.openrocket.swing.gui.dialogs.designdoctor;

import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import info.openrocket.core.designdoctor.RocketDesignDoctor;
import info.openrocket.core.designdoctor.RocketDesignDoctor.Finding;
import info.openrocket.core.designdoctor.RocketDesignDoctor.Report;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.startup.Application;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.swing.gui.util.GUIUtil;
import net.miginfocom.swing.MigLayout;

public final class RocketDesignDoctorDialog extends JDialog {
	private static final long serialVersionUID = 1L;
	private static final String PREFIX = "RocketDesignDoctor.";
	private static final Translator trans = Application.getTranslator();

	public RocketDesignDoctorDialog(Window owner, FlightConfiguration configuration) {
		super(owner, trans.get(PREFIX + "title"), ModalityType.DOCUMENT_MODAL);

		Report report = RocketDesignDoctor.analyze(configuration);
		JPanel content = new JPanel(new MigLayout("fill, insets 12, wrap 1", "[grow,fill]", "[][grow,fill][]"));
		setContentPane(content);
		content.add(createSummary(report));

		JTextArea details = new JTextArea(createDetails(report), 16, 64);
		details.setEditable(false);
		details.setLineWrap(true);
		details.setWrapStyleWord(true);
		details.setFont(new JLabel().getFont());
		details.setCaretPosition(0);
		details.getAccessibleContext().setAccessibleName(trans.get(PREFIX + "findings"));
		content.add(new JScrollPane(details), "grow, push");

		JButton close = new JButton(trans.get("dlg.but.close"));
		close.addActionListener(e -> dispose());
		content.add(close, "right");
		GUIUtil.setDisposableDialogOptions(this, close);
		setLocationRelativeTo(owner);
	}

	private static JPanel createSummary(Report report) {
		JPanel summary = new JPanel(new MigLayout("wrap 2, insets 0", "[leading][grow,fill]"));
		addRow(summary, "configuration", report.configurationName());
		addRow(summary, "activeStages", Integer.toString(report.activeStageCount()));
		addMeasure(summary, "length", report.length(), UnitGroup.UNITS_LENGTH);
		addMeasure(summary, "diameter", report.diameter(), UnitGroup.UNITS_LENGTH);
		addMeasure(summary, "structureMass", report.structureMass(), UnitGroup.UNITS_MASS);
		addMeasure(summary, "structureCg", report.structureCg(), UnitGroup.UNITS_LENGTH);
		addMeasure(summary, "launchMass", report.launchMass(), UnitGroup.UNITS_MASS);
		addMeasure(summary, "launchCg", report.launchCg(), UnitGroup.UNITS_LENGTH);
		addMeasure(summary, "cp", report.cp(), UnitGroup.UNITS_LENGTH);
		addMeasure(summary, "margin", report.margin(), UnitGroup.UNITS_LENGTH);
		addMeasure(summary, "marginCalibers", report.marginCalibers(), UnitGroup.UNITS_STABILITY_CALIBERS);
		return summary;
	}

	private static void addMeasure(JPanel summary, String key, double value, UnitGroup units) {
		String formatted = Double.isFinite(value) ? units.toStringUnit(value) : trans.get(PREFIX + "notEvaluated");
		addRow(summary, key, formatted);
	}

	private static void addRow(JPanel summary, String key, String value) {
		summary.add(new JLabel(trans.get(PREFIX + key)));
		JLabel label = new JLabel();
		label.putClientProperty("html.disable", Boolean.TRUE);
		label.setText(value);
		summary.add(label, "wmax 440");
	}

	private static String createDetails(Report report) {
		String mach = UnitGroup.UNITS_COEFFICIENT.toStringUnit(RocketDesignDoctor.MACH);
		StringBuilder text = new StringBuilder(String.format(trans.get(PREFIX + "conditions"), mach));
		text.append("\n\n").append(trans.get(PREFIX + "stability." + report.stability().name()));
		text.append("\n\n").append(trans.get(PREFIX + "findings")).append("\n");

		if (!report.airframeEvaluated()) {
			text.append(trans.get(PREFIX + "airframeUnavailable")).append("\n");
		} else if (report.findings().isEmpty()) {
			text.append(trans.get(PREFIX + "noWarnings")).append("\n");
		}
		for (Finding finding : report.findings()) {
			text.append("\n").append(finding.message()).append("\n");
			text.append(trans.get(PREFIX + "finding." + finding.type().name())).append("\n");
		}
		return text.toString();
	}
}
