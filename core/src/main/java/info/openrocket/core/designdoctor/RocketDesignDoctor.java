package info.openrocket.core.designdoctor;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.MathUtil;

/**
 * Checks the selected model configuration at a single, explicit static condition.
 */
public final class RocketDesignDoctor {
	public static final double MACH = 0.30;

	private RocketDesignDoctor() {
	}

	public static Report analyze(FlightConfiguration configuration) {
		double length = configuration.getLength();
		double diameter = Double.NaN;
		boolean hasAirframe = false;
		for (RocketComponent component : configuration.getActiveInstances().keySet()) {
			if (component instanceof SymmetricComponent body) {
				hasAirframe = true;
				diameter = MathUtil.max(diameter, 2 * body.getForeRadius(), 2 * body.getAftRadius());
			}
		}
		if (!positiveFinite(diameter)) {
			diameter = Double.NaN;
		}

		CoordinateIF structure = MassCalculator.calculateStructure(configuration).getCM();
		double launchMass = Double.NaN;
		double launchCg = Double.NaN;
		boolean poweredConfiguration = !configuration.getActiveMotors().isEmpty();
		if (poweredConfiguration) {
			CoordinateIF launch = MassCalculator.calculateLaunch(configuration).getCM();
			launchMass = launch.getWeight();
			launchCg = centerOfGravity(launch);
		}

		BarrowmanCalculator calculator = new BarrowmanCalculator();
		WarningSet warnings = new WarningSet();
		if (hasAirframe) {
			calculator.checkGeometry(configuration, configuration.getRocket(), warnings);
		}

		double cp = Double.NaN;
		if (positiveFinite(length) && positiveFinite(configuration.getReferenceLength())) {
			FlightConditions conditions = new FlightConditions(configuration);
			conditions.setMach(MACH);
			conditions.setAOA(0);
			CoordinateIF worstCp = calculator.getWorstCP(configuration, conditions, warnings);
			// A vanishing normal-force slope leaves getWorstCP's position undefined.
			if (Double.isFinite(worstCp.getWeight()) && worstCp.getWeight() > MathUtil.EPSILON
					&& Double.isFinite(worstCp.getX())) {
				cp = worstCp.getX();
			}
		}

		double margin = cp - launchCg;
		StaticStability stability;
		if (!poweredConfiguration) {
			stability = StaticStability.NO_ACTIVE_MOTORS;
		} else if (!Double.isFinite(launchCg)) {
			stability = StaticStability.CG_UNAVAILABLE;
		} else if (!Double.isFinite(margin)) {
			stability = StaticStability.CP_UNAVAILABLE;
		} else if (margin <= 0) {
			stability = StaticStability.CP_AT_OR_AHEAD_OF_CG;
		} else {
			stability = StaticStability.CP_BEHIND_CG;
		}

		List<Finding> findings = new ArrayList<>();
		for (Warning warning : warnings) {
			findings.add(new Finding(FindingType.fromWarning(warning), warning.toString()));
		}
		return new Report(configuration.getName(), configuration.getActiveStageCount(),
				length, diameter, structure.getWeight(), centerOfGravity(structure), launchMass, launchCg,
				cp, margin, margin / diameter, stability, hasAirframe, findings);
	}

	private static double centerOfGravity(CoordinateIF center) {
		if (!Double.isFinite(center.getWeight()) || center.getWeight() <= MassCalculator.MIN_MASS
				|| !Double.isFinite(center.getX())) {
			return Double.NaN;
		}
		return center.getX();
	}

	private static boolean positiveFinite(double value) {
		return Double.isFinite(value) && value > 0;
	}

	public enum StaticStability {
		NO_ACTIVE_MOTORS,
		CG_UNAVAILABLE,
		CP_UNAVAILABLE,
		CP_AT_OR_AHEAD_OF_CG,
		CP_BEHIND_CG
	}

	public enum FindingType {
		OPEN_FORWARD_END(Warning.OPEN_AIRFRAME_FORWARD),
		DIAMETER_STEP(Warning.DIAMETER_DISCONTINUITY),
		AIRFRAME_GAP(Warning.AIRFRAME_GAP),
		AIRFRAME_OVERLAP(Warning.AIRFRAME_OVERLAP),
		ZERO_VOLUME_BODY(Warning.ZERO_VOLUME_BODY),
		POD_FORWARD(Warning.PODSET_FORWARD),
		POD_OVERLAP(Warning.PODSET_OVERLAP),
		THICK_FIN(Warning.THICK_FIN),
		ZERO_AREA_FIN(Warning.ZERO_AREA_FIN),
		OTHER(null);

		private final Warning template;

		FindingType(Warning template) {
			this.template = template;
		}

		private static FindingType fromWarning(Warning warning) {
			for (FindingType type : values()) {
				// The warning set clones templates to attach sources, preserving their IDs.
				if (type.template != null && type.template.getID().equals(warning.getID())) {
					return type;
				}
			}
			return OTHER;
		}
	}

	public record Finding(FindingType type, String message) {
	}

	/**
	 * Dimensions and axial positions are in meters, masses in kilograms. Axial
	 * positions are relative to the model origin. An unavailable value is NaN.
	 */
	public record Report(String configurationName, int activeStageCount, double length, double diameter,
			double structureMass, double structureCg, double launchMass, double launchCg, double cp,
			double margin, double marginCalibers, StaticStability stability, boolean airframeEvaluated,
			List<Finding> findings) {
		public Report {
			findings = List.copyOf(findings);
		}
	}
}
