package info.openrocket.core.simulation.montecarlo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.startup.Application;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.MassComponent;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.simulation.FlightData;
import info.openrocket.core.simulation.FlightEvent;
import info.openrocket.core.simulation.exception.SimulationCancelledException;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.simulation.listeners.system.InterruptListener;

/** Independent uniform manufacturing deviations on private copies of a flight configuration. */
public final class ManufacturingTrials {
	private ManufacturingTrials() { }

	/** Symmetric half-ranges in metres, radians and fractional mass. */
	public record Tolerances(double span, double cant, double position, double mass) {
		public Tolerances {
			for (double value : new double[] {span, cant, position, mass}) {
				if (!Double.isFinite(value) || value < 0) {
					throw new IllegalArgumentException("Tolerances must be finite and nonnegative");
				}
			}
			if (mass >= 1) {
				throw new IllegalArgumentException("Mass tolerance must be less than 100%");
			}
		}
	}

	public record Flight(double altitude, double time) { }
	public record Result(Flight nominal, Flight splitReference, List<Flight> flights, int failed, String firstFailure) { }

	public static Result run(Simulation source, Tolerances tolerance, int count, long seed, IntConsumer progress)
			throws SimulationException {
		if (count < 1 || count > 1000) {
			throw new IllegalArgumentException("Trial count must be between 1 and 1000");
		}
		MonteCarloSimulationRunner.validateExtensions(source);
		Simulation template = source.duplicateForIndependentSimulation();
		template.getOptions().setRandomSeedFixed(true);
		Flight nominal = fly(template.duplicateForIndependentSimulation());
		template.getRocket().enableEvents();
		prepare(template, tolerance);
		Flight reference = fly(template.duplicateForIndependentSimulation());
		Random random = new Random(seed);
		List<Flight> flights = new ArrayList<>();
		String firstFailure = "";
		for (int i = 0; i < count; i++) {
			if (Thread.currentThread().isInterrupted()) {
				throw new CancellationException();
			}
			Simulation trial = template.duplicateForIndependentSimulation();
			trial.getRocket().enableEvents();
			vary(trial, tolerance, random);
			try {
				flights.add(fly(trial));
			} catch (SimulationCancelledException cancelled) {
				throw new CancellationException();
			} catch (SimulationException failed) {
				if (firstFailure.isEmpty()) {
					firstFailure = failed.getLocalizedMessage() == null ? failed.getClass().getSimpleName() : failed.getLocalizedMessage();
				}
			}
			progress.accept(i + 1);
		}
		return new Result(nominal, reference, List.copyOf(flights), count - flights.size(), firstFailure);
	}

	static void prepare(Simulation simulation, Tolerances t) {
		boolean fins = false;
		boolean trapezoids = false;
		boolean mass = false;
		for (RocketComponent part : simulation.getActiveConfiguration().getActiveComponents()) {
			if ((part instanceof FinSet || part instanceof MassComponent)
					&& part.getMassOverriddenBy() == null && part.getMass() > 0) {
				mass = true;
			}
			if (part instanceof FinSet set) {
				fins = true;
				if (set instanceof TrapezoidFinSet trapezoid) {
					trapezoids = true;
					if (t.span > 0 && t.span >= trapezoid.getHeight()) {
						throw new IllegalArgumentException(Application.getTranslator().get("Manufacturing.invalidSpan") + " " + set.getName());
					}
				}
				if (Math.abs(set.getCantAngle()) + t.cant > FinSet.MAX_CANT_RADIANS) {
					throw new IllegalArgumentException(Application.getTranslator().get("Manufacturing.invalidCant") + " " + set.getName());
				}
				if (t.cant > 0 || t.position > 0 || (set instanceof TrapezoidFinSet && t.span > 0)
						|| (t.mass > 0 && set.getMassOverriddenBy() == null)) {
					set.splitFins();
				}
			}
		}
		if ((!fins && (t.cant > 0 || t.position > 0)) || (!trapezoids && t.span > 0) || (!mass && t.mass > 0)) {
			throw new IllegalArgumentException(Application.getTranslator().get("Manufacturing.noTarget"));
		}
	}

	static void vary(Simulation sim, Tolerances t, Random random) {
		for (RocketComponent part : sim.getActiveConfiguration().getActiveComponents()) {
			if (part instanceof FinSet fin) {
				if (fin instanceof TrapezoidFinSet trapezoid && t.span > 0) {
					trapezoid.setHeight(trapezoid.getHeight() + sample(random, t.span));
				}
				if (t.cant > 0) {
					fin.setCantAngle(fin.getCantAngle() + sample(random, t.cant));
				}
				if (t.position > 0) {
					double top = fin.getAxialOffset(AxialMethod.TOP);
					fin.setAxialMethod(AxialMethod.TOP);
					fin.setAxialOffset(top + sample(random, t.position));
				}
			}
			if ((part instanceof FinSet || part instanceof MassComponent) && t.mass > 0
					&& part.getMassOverriddenBy() == null) {
				part.setOverrideMass(part.getMass() * (1 + sample(random, t.mass)));
				part.setMassOverridden(true);
			}
		}
	}

	private static double sample(Random random, double range) {
		return MonteCarloDistribution.UNIFORM.sample(random, range);
	}

	private static Flight fly(Simulation sim) throws SimulationException {
		sim.simulate(InterruptListener.INSTANCE);
		FlightData data = sim.getSimulatedData();
		if (data == null || data.getBranchCount() == 0) {
			throw new SimulationException(Application.getTranslator().get("Manufacturing.noData"));
		}
		var events = data.getBranch(0).getEvents().stream().map(FlightEvent::getType).toList();
		if (events.contains(FlightEvent.Type.SIM_ABORT) || !events.contains(FlightEvent.Type.APOGEE)
				|| !events.contains(FlightEvent.Type.GROUND_HIT)
				|| !Double.isFinite(data.getMaxAltitude()) || !Double.isFinite(data.getFlightTime())) {
			throw new SimulationException(Application.getTranslator().get("Manufacturing.incomplete"));
		}
		return new Flight(data.getMaxAltitude(), data.getFlightTime());
	}
}
