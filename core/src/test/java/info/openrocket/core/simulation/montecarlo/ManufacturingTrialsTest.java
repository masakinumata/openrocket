package info.openrocket.core.simulation.montecarlo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.simulation.montecarlo.ManufacturingTrials.Tolerances;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

class ManufacturingTrialsTest extends BaseTestCase {
	private static Simulation source() {
		var sim = new Simulation(TestRockets.makeEstesAlphaIII());
		sim.setFlightConfigurationId(TestRockets.TEST_FCID_0);
		sim.getOptions().setLaunchRodLength(1);
		sim.getOptions().setTimeStep(0.05);
		sim.getOptions().setMaxSimulationTime(120);
		sim.getOptions().getAverageWindModel().setAverage(2);
		sim.getOptions().getAverageWindModel().setStandardDeviation(0);
		return sim;
	}

	private static List<TrapezoidFinSet> fins(Simulation sim) {
		return sim.getActiveConfiguration().getActiveComponents().stream()
				.filter(TrapezoidFinSet.class::isInstance).map(TrapezoidFinSet.class::cast).toList();
	}

	@Test
	void independentFinsStayWithinSpecifiedTolerances() {
		var source = source();
		var nominal = fins(source).get(0);
		var t = new Tolerances(0.001, 0.01, 0.002, 0.03);
		var trial = source.duplicateForIndependentSimulation();
		trial.getRocket().enableEvents();
		ManufacturingTrials.prepare(trial, t);
		ManufacturingTrials.vary(trial, t, new Random(42));
		var varied = fins(trial);
		assertEquals(nominal.getFinCount(), varied.size());
		assertTrue(varied.size() > 1);
		for (var fin : varied) {
			assertEquals(1, fin.getFinCount());
			assertEquals(nominal.getHeight(), fin.getHeight(), t.span());
			assertEquals(nominal.getCantAngle(), fin.getCantAngle(), t.cant());
			assertEquals(nominal.getAxialOffset(fin.getAxialMethod()), fin.getAxialOffset(), t.position());
			assertEquals(fin.getComponentMass(), fin.getMass(), fin.getComponentMass() * t.mass());
		}
		assertNotEquals(varied.get(0).getCantAngle(), varied.get(1).getCantAngle());
		assertEquals(1, fins(source).size());
		assertFalse(nominal.isMassOverridden());
	}

	@Test
	void zeroErrorReproducesNominalFlight() throws Exception {
		var result = ManufacturingTrials.run(source(), new Tolerances(0, 0, 0, 0), 2, 11, n -> {});
		assertEquals(0, result.failed());
		assertTrue(result.nominal().altitude() > 0);
		for (var trial : result.flights()) {
			assertEquals(result.nominal().altitude(), trial.altitude(), 1e-6);
			assertEquals(result.nominal().time(), trial.time(), 1e-6);
		}
	}

	@Test
	void sampledFlightsHaveMeasurableSpread() throws Exception {
		var result = ManufacturingTrials.run(source(), new Tolerances(.001, .008, .001, .02), 4, 101, n -> {});
		assertEquals(4, result.flights().size());
		assertTrue(result.flights().stream().mapToDouble(ManufacturingTrials.Flight::altitude).max().orElseThrow()
				- result.flights().stream().mapToDouble(ManufacturingTrials.Flight::altitude).min().orElseThrow() > .01);
	}
}
