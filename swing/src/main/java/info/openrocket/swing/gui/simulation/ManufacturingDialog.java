package info.openrocket.swing.gui.simulation;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.ToDoubleFunction;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

import net.miginfocom.swing.MigLayout;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.plot.PlotOrientation;

import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.Simulation;
import info.openrocket.core.simulation.montecarlo.ManufacturingTrials;
import info.openrocket.core.simulation.montecarlo.ManufacturingTrials.Flight;
import info.openrocket.core.simulation.montecarlo.ManufacturingTrials.Result;
import info.openrocket.core.startup.Application;
import info.openrocket.core.unit.Unit;
import info.openrocket.core.unit.UnitGroup;

public final class ManufacturingDialog extends JDialog {
	private final JComboBox<String> simulation;
	private final JPanel inputs = new JPanel(new MigLayout("wrap 2", "[][grow,fill]"));
	private final JSpinner span = spinner("span", new SpinnerNumberModel(1.0, 0.0, 1000.0, 0.1));
	private final JSpinner cant = spinner("cant", new SpinnerNumberModel(0.5, 0.0, 15.0, 0.1));
	private final JSpinner axial = spinner("position", new SpinnerNumberModel(1.0, 0.0, 1000.0, 0.1));
	private final JSpinner mass = spinner("mass", new SpinnerNumberModel(2.0, 0.0, 99.0, 0.1));
	private final JSpinner count = spinner("count", new SpinnerNumberModel(30, 1, 1000, 1));
	private final JSpinner seed = spinner("seed", new SpinnerNumberModel(1, 0, Integer.MAX_VALUE, 1));
	private final JProgressBar progress = new JProgressBar();
	private final JButton run = new JButton(label("run"));
	private SwingWorker<Result, Integer> worker;

	public ManufacturingDialog(Frame owner, OpenRocketDocument document) {
		super(owner, label("title"), true);
		List<Simulation> simulations = document.getSimulations();
		simulation = new JComboBox<>(simulations.stream().map(Simulation::getName).toArray(String[]::new));
		inputs.add(new JLabel(label("simulation")), 0);
		inputs.add(simulation, 1);
		inputs.add(new JLabel(label("hint")), "span 2");
		inputs.add(progress, "span 2, growx");
		inputs.add(run, "span 2, right");
		setContentPane(inputs);
		run.setEnabled(!simulations.isEmpty());
		if (simulations.isEmpty()) {
			inputs.add(new JLabel(label("empty")), "span 2");
		}
		run.addActionListener(e -> start(simulations.get(simulation.getSelectedIndex())));
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				if (worker != null) {
					worker.cancel(true);
				}
			}
		});
		pack();
		setLocationRelativeTo(owner);
	}

	private static String label(String key) {
		return Application.getTranslator().get("Manufacturing." + key);
	}

	private JSpinner spinner(String key, SpinnerNumberModel model) {
		JSpinner spinner = new JSpinner(model);
		JLabel caption = new JLabel(label(key));
		caption.setLabelFor(spinner);
		inputs.add(caption);
		inputs.add(spinner);
		return spinner;
	}

	private void start(Simulation source) {
		try {
			for (JSpinner spinner : List.of(span, cant, axial, mass, count, seed)) {
				spinner.commitEdit();
			}
		} catch (ParseException invalid) {
			JOptionPane.showMessageDialog(this, label("invalid"));
			return;
		}
		var tolerance = new ManufacturingTrials.Tolerances((double) span.getValue() / 1000,
				Math.toRadians((double) cant.getValue()), (double) axial.getValue() / 1000,
				(double) mass.getValue() / 100);
		int trials = (int) count.getValue();
		int randomSeed = (int) seed.getValue();
		Simulation snapshot = source.duplicateForIndependentSimulation();
		for (var field : inputs.getComponents()) {
			field.setEnabled(false);
		}
		progress.setEnabled(true);
		progress.setMaximum(trials);
		progress.setValue(0);
		progress.setStringPainted(true);
		worker = new SwingWorker<>() {
			@Override
			protected Result doInBackground() throws Exception {
				return ManufacturingTrials.run(snapshot, tolerance, trials, randomSeed, this::publish);
			}
			@Override
			protected void process(List<Integer> values) {
				progress.setValue(values.get(values.size() - 1));
			}
			@Override
			protected void done() {
				if (isCancelled() || !isDisplayable()) {
					return;
				}
				try {
					showResults(get());
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				} catch (CancellationException ignored) {
					// Closing the dialog cancels the trials.
				} catch (ExecutionException failure) {
					JOptionPane.showMessageDialog(ManufacturingDialog.this, failure.getCause().getLocalizedMessage(),
							label("title"), JOptionPane.ERROR_MESSAGE);
				} finally {
					for (var field : inputs.getComponents()) {
						field.setEnabled(true);
					}
				}
			}
		};
		worker.execute();
	}

	private void showResults(Result result) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.add(new JLabel(String.format(label("summary"), result.flights().size(), result.failed(),
				seed.getValue(), span.getValue(), cant.getValue(), axial.getValue(), mass.getValue())), BorderLayout.NORTH);
		if (!result.flights().isEmpty()) {
			JTabbedPane tabs = new JTabbedPane();
			tabs.addTab(label("altitude"), plot("altitude", result, Flight::altitude, UnitGroup.UNITS_DISTANCE.getDefaultUnit()));
			tabs.addTab(label("time"), plot("time", result, Flight::time, UnitGroup.UNITS_LONG_TIME.getDefaultUnit()));
			panel.add(tabs);
		}
		if (result.failed() > 0) {
			panel.add(new JLabel(label("failure") + " " + result.firstFailure()), BorderLayout.SOUTH);
		}
		JOptionPane.showMessageDialog(this, panel, label("title"), JOptionPane.PLAIN_MESSAGE);
	}

	private JPanel plot(String key, Result result, ToDoubleFunction<Flight> metric, Unit unit) {
		List<Double> values = result.flights().stream().mapToDouble(metric).boxed().toList();
		String axis = label(key) + " (" + unit.getUnit() + ")";
		var dataset = MonteCarloMetricsPanel.createHistogramDataset(axis, values, unit);
		var chart = ChartFactory.createHistogram(null, axis, label("frequency"), dataset,
				PlotOrientation.VERTICAL, false, true, false);
		JPanel panel = new JPanel(new BorderLayout());
		ChartPanel chartPanel = new ChartPanel(chart);
		chartPanel.setPreferredSize(new Dimension(550, 300));
		panel.add(chartPanel);
		panel.add(new JLabel(String.format(label("references"),
				unit.toStringUnit(metric.applyAsDouble(result.nominal())),
				unit.toStringUnit(metric.applyAsDouble(result.splitReference())))), BorderLayout.SOUTH);
		return panel;
	}
}
