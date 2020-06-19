/*
 * PropagatorBuilder.java - Wrapper for Orekit's propagator builder.
 * Copyright (C) 2018-2020 University of Texas
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.astria;

import java.util.Arrays;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.conversion.NumericalPropagatorBuilder;
import org.orekit.propagation.conversion.ODEIntegratorBuilder;
import org.orekit.propagation.integration.AdditionalEquations;
import org.orekit.propagation.numerical.NumericalPropagator;
import org.orekit.propagation.sampling.OrekitFixedStepHandler;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.ParameterDriversList;

public final class PropagatorBuilder extends NumericalPropagatorBuilder
{
    private final Settings odCfg;
    private final DMCEquations dmcEqns;
    private final OrekitFixedStepHandler stepHandler;
    protected boolean enableDMC;

    public PropagatorBuilder(Settings cfg, Orbit orb, ODEIntegratorBuilder ode,
			     PositionAngle ang, double pos, OrekitFixedStepHandler handler, boolean enableDMC)
    {
	super(orb, ode, ang, pos);
	this.odCfg = cfg;
	this.dmcEqns = new DMCEquations();
	this.stepHandler = handler;
	this.enableDMC = enableDMC;
    }

    @Override public NumericalPropagator buildPropagator(double[] par)
    {
	NumericalPropagator prop = super.buildPropagator(par);
	if (stepHandler != null)
	    prop.setMasterMode(odCfg.propStep, stepHandler);

	if (odCfg.estmDMCCorrTime > 0.0 && odCfg.estmDMCSigmaPert > 0.0)
	{
	    prop.addAdditionalEquations(dmcEqns);
	    ParameterDriversList plst = getPropagationParametersDrivers();
	    prop.setInitialState(prop.getInitialState().addAdditionalState(Estimation.DMC_ACC_PROP,
									   plst.findByName(Estimation.DMC_ACC_ESTM[0]).getValue(),
									   plst.findByName(Estimation.DMC_ACC_ESTM[1]).getValue(),
									   plst.findByName(Estimation.DMC_ACC_ESTM[2]).getValue()));
	}

	odCfg.addEventHandlers(prop);
	return(prop);
    }

    class DMCEquations implements AdditionalEquations
    {
	private double[] accEci;

	public DMCEquations()
	{
	    accEci = new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
	}

	public void init(SpacecraftState sta, AbsoluteDate tgt)
	{
	}

	public String getName()
	{
	    return(Estimation.DMC_ACC_PROP);
	}

	public double[] computeDerivatives(SpacecraftState sta, double[] pdot)
	{
	    if (!enableDMC)
	    {
		Arrays.fill(pdot, 0.0);
		Arrays.fill(accEci, 0.0);
		return(accEci);
	    }

	    double[] acc = sta.getAdditionalState(Estimation.DMC_ACC_PROP);
	    for (int i = 0; i < 3; i++)
	    {
		accEci[i+3] = acc[i];
		pdot[i] = -acc[i]/odCfg.estmDMCCorrTime;
	    }

	    return(accEci);
	}
    }
}
