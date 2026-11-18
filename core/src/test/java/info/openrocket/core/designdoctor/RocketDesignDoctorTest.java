package info.openrocket.core.designdoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.designdoctor.RocketDesignDoctor.FindingType;
import info.openrocket.core.designdoctor.RocketDesignDoctor.Report;
import info.openrocket.core.designdoctor.RocketDesignDoctor.StaticStability;
import info.openrocket.core.motor.MotorConfiguration;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

class RocketDesignDoctorTest extends BaseTestCase {
	private static final double TOLERANCE = 1e-6;

	@Test
	void emptyRocketDoesNotReportPlaceholderDimensionsOrCenters() {
		Rocket rocket = new Rocket();
		rocket.addChild(new AxialStage());
		rocket.enableEvents();

		Report report = RocketDesignDoctor.analyze(rocket.getSelectedConfiguration());

		assertEquals(0, report.activeStageCount());
		assertEquals(0, report.length());
		assertEquals(0, report.structureMass());
		assertTrue(Double.isNaN(report.diameter()));
		assertTrue(Double.isNaN(report.structureCg()));
		assertTrue(Double.isNaN(report.cp()));
		assertTrue(Double.isNaN(report.margin()));
		assertFalse(report.airframeEvaluated());
		assertTrue(report.findings().isEmpty());
	}

	@Test
	void motorlessConfigurationReportsStructureCgAndCpWithoutLaunchValues() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();

		Report report = RocketDesignDoctor.analyze(rocket.getEmptyConfiguration());

		assertEquals(0.025268291846128787, report.structureMass(), TOLERANCE);
		assertEquals(0.19176843580003, report.structureCg(), TOLERANCE);
		assertTrue(Double.isFinite(report.cp()));
		assertTrue(Double.isNaN(report.launchMass()));
		assertTrue(Double.isNaN(report.launchCg()));
		assertTrue(Double.isNaN(report.margin()));
		assertEquals(StaticStability.NO_ACTIVE_MOTORS, report.stability());
	}

	@Test
	void poweredConfigurationReportsLaunchCgAndStaticMargin() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		rocket.setSelectedConfiguration(TestRockets.TEST_FCID_0);

		Report report = RocketDesignDoctor.analyze(rocket.getSelectedConfiguration());

		// The hand-calculated Alpha III reference in BarrowmanCalculatorTest uses one azimuth.
		double referenceCp = (2 * 0.03235 + 28.82053382 * (0.22 + 0.018524171835048092)) / (2 + 28.82053382);
		double expectedLaunchCg = 0.20996446974544236;
		assertEquals(1, report.activeStageCount());
		assertEquals(0.024, report.diameter(), TOLERANCE);
		assertEquals(0.04166829184612879, report.launchMass(), TOLERANCE);
		assertEquals(expectedLaunchCg, report.launchCg(), TOLERANCE);
		assertEquals(referenceCp, report.cp(), TOLERANCE);
		double expectedMargin = report.cp() - expectedLaunchCg;
		assertEquals(expectedMargin, report.margin(), TOLERANCE);
		assertEquals(expectedMargin / 0.024, report.marginCalibers(), TOLERANCE);
		assertEquals(StaticStability.CP_BEHIND_CG, report.stability());
	}

	@Test
	void cgMovedAftProducesANonRestoringStaticMarginFinding() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		rocket.setSelectedConfiguration(TestRockets.TEST_FCID_0);
		AxialStage stage = (AxialStage) rocket.getChild(0);
		stage.setCGOverridden(true);
		stage.setSubcomponentsOverriddenCG(true);
		stage.setOverrideCGX(0.40);

		Report report = RocketDesignDoctor.analyze(rocket.getSelectedConfiguration());

		assertTrue(report.margin() < 0);
		assertEquals(StaticStability.CP_AT_OR_AHEAD_OF_CG, report.stability());
	}

	@Test
	void poweredBodyTubeWithNoNormalForceHasNoCpOrMargin() {
		Rocket rocket = new Rocket();
		AxialStage stage = new AxialStage();
		rocket.addChild(stage);
		BodyTube tube = new BodyTube(0.2, 0.012, 0.001);
		stage.addChild(tube);
		tube.setMotorMount(true);
		FlightConfiguration configuration = rocket.createFlightConfiguration(TestRockets.TEST_FCID_0);
		MotorConfiguration motor = new MotorConfiguration(tube, configuration.getId());
		motor.setMotor(TestRockets.generateMotor_A8_18mm());
		tube.setMotorConfig(motor, configuration.getId());
		rocket.setSelectedConfiguration(configuration.getId());
		configuration.setAllStages();
		rocket.enableEvents();

		Report report = RocketDesignDoctor.analyze(configuration);

		assertTrue(Double.isFinite(report.launchCg()));
		assertTrue(Double.isNaN(report.cp()));
		assertTrue(Double.isNaN(report.margin()));
		assertEquals(StaticStability.CP_UNAVAILABLE, report.stability());
	}

	@Test
	void reportsAirframeGapWithTheAffectedComponents() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		BodyTube tube = (BodyTube) rocket.getChild(0).getChild(1);
		tube.setAxialMethod(AxialMethod.ABSOLUTE);
		tube.setAxialOffset(0.08);

		Report report = RocketDesignDoctor.analyze(rocket.getSelectedConfiguration());

		assertTrue(report.airframeEvaluated());
		assertTrue(report.findings().stream().anyMatch(finding -> finding.type() == FindingType.AIRFRAME_GAP
				&& finding.message().contains("Nose Cone") && finding.message().contains("Body Tube")));
	}

	@Test
	void excludedStageDoesNotContributeMotorsOrGeometry() {
		Rocket rocket = TestRockets.makeEstesAlphaIII();
		rocket.setSelectedConfiguration(TestRockets.TEST_FCID_0);
		FlightConfiguration configuration = rocket.getSelectedConfiguration();
		configuration.clearAllStages();

		Report report = RocketDesignDoctor.analyze(configuration);

		assertEquals(0, report.activeStageCount());
		assertEquals(0, report.length());
		assertEquals(0, report.structureMass());
		assertTrue(Double.isNaN(report.launchMass()));
		assertTrue(Double.isNaN(report.cp()));
		assertEquals(StaticStability.NO_ACTIVE_MOTORS, report.stability());
		assertFalse(report.airframeEvaluated());
	}
}
