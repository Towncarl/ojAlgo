/*
 * Copyright 1997-2016 Optimatika (www.optimatika.se)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.ojalgo.optimisation.convex;

import static org.ojalgo.constant.PrimitiveMath.*;
import static org.ojalgo.function.PrimitiveFunction.*;

import org.ojalgo.function.aggregator.Aggregator;
import org.ojalgo.matrix.decomposition.DecompositionStore;
import org.ojalgo.matrix.store.ElementsConsumer;
import org.ojalgo.matrix.store.MatrixStore;
import org.ojalgo.matrix.store.PhysicalStore;
import org.ojalgo.matrix.store.PrimitiveDenseStore;
import org.ojalgo.optimisation.linear.LinearSolver;
import org.ojalgo.type.IndexSelector;

abstract class ActiveSetSolver extends ConstrainedSolver {

    final IndexSelector myActivator;
    int myConstraintToInclude = -1;
    MatrixStore<Double> myInvQC;
    final PrimitiveDenseStore myIterationL;
    final PrimitiveDenseStore myIterationX;

    ActiveSetSolver(final Builder matrices, final Options solverOptions) {

        super(matrices, solverOptions);

        final int tmpCountVariables = this.countVariables();
        final int tmpCountInequalityConstraints = this.countInequalityConstraints();
        final int tmpCountEqualityConstraints = this.countEqualityConstraints();

        myActivator = new IndexSelector(tmpCountInequalityConstraints);

        myIterationL = PrimitiveDenseStore.FACTORY.makeZero(tmpCountEqualityConstraints + tmpCountInequalityConstraints, 1L);
        myIterationX = PrimitiveDenseStore.FACTORY.makeZero(tmpCountVariables, 1L);
    }

    public int countExcluded() {
        return myActivator.countExcluded();
    }

    public int countIncluded() {
        return myActivator.countIncluded();
    }

    public int[] getExcluded() {
        return myActivator.getExcluded();
    }

    public int[] getIncluded() {
        return myActivator.getIncluded();
    }

    public int getLastExcluded() {
        return myActivator.getLastExcluded();
    }

    public int getLastIncluded() {
        return myActivator.getLastIncluded();
    }

    protected boolean checkFeasibility(final boolean onlyExcluded) {

        boolean retVal = true;

        if (!onlyExcluded) {

            if (this.hasEqualityConstraints()) {
                final MatrixStore<Double> tmpAEX = this.getAEX();
                final MatrixStore<Double> tmpBE = this.getBE();
                for (int i = 0; retVal && (i < tmpBE.countRows()); i++) {
                    if (options.slack.isDifferent(tmpBE.doubleValue(i), tmpAEX.doubleValue(i))) {
                        retVal = false;
                    }
                }
            }

            if (this.hasInequalityConstraints() && (myActivator.countIncluded() > 0)) {
                final int[] tmpIncluded = myActivator.getIncluded();
                final MatrixStore<Double> tmpAIX = this.getAIX(tmpIncluded);
                final MatrixStore<Double> tmpBI = this.getBI(tmpIncluded);
                for (int i = 0; retVal && (i < tmpIncluded.length); i++) {
                    final double tmpBody = tmpAIX.doubleValue(i);
                    final double tmpRHS = tmpBI.doubleValue(i);
                    if ((tmpBody > tmpRHS) && options.slack.isDifferent(tmpRHS, tmpBody)) {
                        retVal = false;
                    }
                }
            }
        }

        if (this.hasInequalityConstraints() && (myActivator.countExcluded() > 0)) {
            final int[] tmpExcluded = myActivator.getExcluded();
            final MatrixStore<Double> tmpAIX = this.getAIX(tmpExcluded);
            final MatrixStore<Double> tmpBI = this.getBI(tmpExcluded);
            for (int i = 0; retVal && (i < tmpExcluded.length); i++) {
                final double tmpBody = tmpAIX.doubleValue(i);
                final double tmpRHS = tmpBI.doubleValue(i);
                if ((tmpBody > tmpRHS) && options.slack.isDifferent(tmpRHS, tmpBody)) {
                    retVal = false;
                }
            }
        }

        return retVal;
    }

    @Override
    protected MatrixStore<Double> extractSolution() {
        return super.extractSolution();
    }

    @Override
    protected final MatrixStore<Double> getIterationKKT() {
        return this.getIterationKKT(myActivator.getIncluded());
    }

    protected final MatrixStore<Double> getIterationKKT(final int[] included) {
        final MatrixStore<Double> tmpIterationQ = this.getIterationQ();
        final MatrixStore<Double> tmpIterationA = this.getIterationA(included);
        return tmpIterationQ.builder().right(tmpIterationA.transpose()).below(tmpIterationA).build();
    }

    @Override
    protected final MatrixStore<Double> getIterationRHS() {
        return this.getIterationRHS(myActivator.getIncluded());
    }

    protected final MatrixStore<Double> getIterationRHS(final int[] included) {
        final MatrixStore<Double> tmpIterationC = this.getIterationC();
        final MatrixStore<Double> tmpIterationB = this.getIterationB(included);
        return tmpIterationC.builder().below(tmpIterationB).build();
    }

    @Override
    protected final boolean initialise(final Result kickStarter) {

        super.initialise(kickStarter);

        final MatrixStore<Double> tmpQ = this.getQ();
        final MatrixStore<Double> tmpC = this.getC();
        final MatrixStore<Double> tmpAE = this.getAE();
        final MatrixStore<Double> tmpBE = this.getBE();
        final MatrixStore<Double> tmpAI = this.getAI();
        final MatrixStore<Double> tmpBI = this.getBI();

        final int tmpNumVars = (int) tmpC.countRows();
        final int tmpNumEqus = tmpAE != null ? (int) tmpAE.countRows() : 0;
        final int tmpNumInes = tmpAI != null ? (int) tmpAI.countRows() : 0;

        final DecompositionStore<Double> tmpX = this.getX();

        myActivator.excludeAll();

        boolean tmpFeasible = false;
        final boolean tmpUsableKickStarter = (kickStarter != null) && kickStarter.getState().isApproximate();

        if (tmpUsableKickStarter) {
            this.fillX(kickStarter);
            tmpFeasible = this.checkFeasibility(false);
        }

        if (!tmpFeasible) {
            tmpFeasible = this.formLP(tmpQ, tmpC, tmpAE, tmpBE, tmpAI, tmpBI, tmpNumVars, tmpNumEqus, tmpNumInes, tmpX, tmpUsableKickStarter);
        }

        if (tmpFeasible) {

            this.initSolution(tmpBI, tmpNumVars, tmpNumEqus);

        } else {

            this.setState(State.INFEASIBLE);

            this.resetX();
        }

        if (this.isDebug()) {

            this.debug("Initial solution: {}", tmpX.copy().asList());
            if (tmpAE != null) {
                this.debug("Initial E-slack: {}", this.getSE().copy().asList());
            }
            if (tmpAI != null) {
                this.debug("Initial I-included-slack: {}", this.getSI(myActivator.getIncluded()).copy().asList());
                this.debug("Initial I-excluded-slack: {}", this.getSI(myActivator.getExcluded()).copy().asList());
            }
        }

        return this.getState().isFeasible();
    }

    @Override
    protected final boolean needsAnotherIteration() {

        if (this.isDebug()) {
            this.debug("\nNeedsAnotherIteration?");
            this.debug(myActivator.toString());
        }

        int tmpToInclude = -1;
        int tmpToExclude = -1;

        if (this.hasInequalityConstraints()) {
            tmpToInclude = this.suggestConstraintToInclude();
            if (tmpToInclude == -1) {
                tmpToExclude = this.suggestConstraintToExclude();
            }
        }

        if (this.isDebug()) {
            this.debug("Suggested to include: {}", tmpToInclude);
            this.debug("Suggested to exclude: {}", tmpToExclude);
        }

        if (tmpToExclude == -1) {
            if (tmpToInclude == -1) {
                // Suggested to do nothing
                this.setState(State.OPTIMAL);
                return false;
            } else {
                // Only suggested to include
                myActivator.include(tmpToInclude);
                this.setState(State.APPROXIMATE);
                return true;
            }
        } else {
            if (tmpToInclude == -1) {
                this.excludeAndRemove(tmpToExclude);
                this.setState(State.APPROXIMATE);
                return true;
            } else {
                this.excludeAndRemove(tmpToExclude);
                myActivator.include(tmpToInclude);
                this.setState(State.APPROXIMATE);
                return true;
            }
        }
    }

    /**
     * Find the minimum (largest negative) lagrange multiplier - for the active inequalities - to potentially
     * deactivate.
     */
    protected int suggestConstraintToExclude() {

        int retVal = -1;

        final int[] tmpIncluded = myActivator.getIncluded();
        final int tmpLastIncluded = myActivator.getLastIncluded();
        int tmpIndexOfLast = -1;

        double tmpMin = POSITIVE_INFINITY;
        double tmpVal;

        // final MatrixStore<Double> tmpLI = this.getLI(tmpIncluded);
        final MatrixStore<Double> tmpLI = myIterationL.builder().offsets(this.countEqualityConstraints(), 0).row(tmpIncluded).get();

        if (this.isDebug() && (tmpLI.count() > 0L)) {
            this.debug("Looking for the largest negative lagrange multiplier among these: {}.", tmpLI.copy().asList());
        }

        for (int i = 0; i < tmpLI.countRows(); i++) {

            if (tmpIncluded[i] != tmpLastIncluded) {

                tmpVal = tmpLI.doubleValue(i, 0);

                if ((tmpVal < ZERO) && (tmpVal < tmpMin) && !options.solution.isZero(tmpVal)) {
                    tmpMin = tmpVal;
                    retVal = i;
                    if (this.isDebug()) {
                        this.debug("Best so far: {} @ {} ({}).", tmpMin, retVal, tmpIncluded[i]);
                    }
                }

            } else {

                tmpIndexOfLast = i;
            }
        }

        if ((retVal < 0) && (tmpIndexOfLast >= 0)) {

            tmpVal = tmpLI.doubleValue(tmpIndexOfLast, 0);

            if ((tmpVal < ZERO) && (tmpVal < tmpMin) && !options.solution.isZero(tmpVal)) {
                tmpMin = tmpVal;
                retVal = tmpIndexOfLast;
                if (this.isDebug()) {
                    this.debug("Only the last included needs to be excluded: {} @ {} ({}).", tmpMin, retVal, tmpIncluded[retVal]);
                }
            }
        }

        return retVal >= 0 ? tmpIncluded[retVal] : retVal;
    }

    /**
     * Find minimum (largest negative) slack - for the inactive inequalities - to potentially activate.
     * Negative slack means the constraint is violated. Need to make sure it is enforced by activating it.
     */
    protected int suggestConstraintToInclude() {
        return myConstraintToInclude;
    }

    abstract void excludeAndRemove(int toExclude);

    final boolean formLP(final MatrixStore<Double> tmpQ, final MatrixStore<Double> tmpC, final MatrixStore<Double> tmpAE, final MatrixStore<Double> tmpBE,
            final MatrixStore<Double> tmpAI, final MatrixStore<Double> tmpBI, final int tmpNumVars, final int tmpNumEqus, final int tmpNumInes,
            final DecompositionStore<Double> tmpX, final boolean tmpUsableKickStarter) {
        boolean tmpFeasible;
        // Form LP to check feasibility

        final MatrixStore<Double> tmpGradient = tmpUsableKickStarter ? tmpQ.multiply(tmpX).subtract(tmpC) : tmpC.negate();
        final MatrixStore<Double> tmpLinearC = tmpGradient.builder().below(tmpGradient.negate()).below(tmpNumInes).build();
        // final MatrixStore<Double> tmpLinearC = MatrixStore.PRIMITIVE.makeZero(tmpNumVars + tmpNumVars + tmpNumInes, 1).get();

        final LinearSolver.Builder tmpLinearBuilder = LinearSolver.getBuilder(tmpLinearC);

        MatrixStore<Double> tmpAEpart = null;
        MatrixStore<Double> tmpBEpart = null;

        if (tmpNumEqus > 0) {
            tmpAEpart = tmpAE.builder().right(tmpAE.negate()).right(tmpNumInes).build();
            tmpBEpart = tmpBE;
        }

        if (tmpNumInes > 0) {
            final MatrixStore<Double> tmpAIpart = tmpAI.builder().right(tmpAI.negate()).right(MatrixStore.PRIMITIVE.makeIdentity(tmpNumInes).get()).build();
            final MatrixStore<Double> tmpBIpart = tmpBI;
            if (tmpAEpart != null) {
                tmpAEpart = tmpAEpart.builder().below(tmpAIpart).build();
                tmpBEpart = tmpBEpart.builder().below(tmpBIpart).build();
            } else {
                tmpAEpart = tmpAIpart;
                tmpBEpart = tmpBIpart;
            }
        }

        if (tmpAEpart != null) {

            final PhysicalStore<Double> tmpLinearAE = tmpAEpart.copy();
            final PhysicalStore<Double> tmpLinearBE = tmpBEpart.copy();

            for (int i = 0; i < tmpLinearBE.countRows(); i++) {
                if (tmpLinearBE.doubleValue(i) < 0.0) {
                    tmpLinearAE.modifyRow(i, 0, NEGATE);
                    tmpLinearBE.modifyRow(i, 0, NEGATE);
                }
            }

            tmpLinearBuilder.equalities(tmpLinearAE, tmpLinearBE);
        }

        final LinearSolver tmpLinearSolver = tmpLinearBuilder.build();

        final Result tmpLinearResult = tmpLinearSolver.solve();

        if (tmpFeasible = tmpLinearResult.getState().isFeasible()) {

            final ElementsConsumer<Double> tmpLI = myIterationL.regionByOffsets(tmpNumEqus, 0);

            for (int i = 0; i < tmpNumVars; i++) {
                this.setX(i, tmpLinearResult.doubleValue(i) - tmpLinearResult.doubleValue(tmpNumVars + i));
            }
            @SuppressWarnings("deprecation")
            final double[] tmpResidual = tmpLinearSolver.getResidualCosts();
            for (int i = tmpNumVars * 2; i < tmpResidual.length; i++) {
                final int tmpIndexToInclude = i - (2 * tmpNumVars);
                // this.setLI(tmpIndexToInclude, tmpResidual[i]);
                tmpLI.set(tmpIndexToInclude, tmpResidual[i]);
            }
        }
        return tmpFeasible;
    }

    @Override
    final MatrixStore<Double> getIterationA() {
        return this.getIterationA(myActivator.getIncluded());
    }

    abstract MatrixStore<Double> getIterationA(int[] included);

    @Override
    final MatrixStore<Double> getIterationB() {
        return this.getIterationB(myActivator.getIncluded());
    }

    abstract MatrixStore<Double> getIterationB(int[] included);

    @Override
    final MatrixStore<Double> getIterationC() {

        //        final MatrixStore<Double> tmpQ = this.getQ();
        //        final MatrixStore<Double> tmpC = this.getC();
        //
        //        final PhysicalStore<Double> tmpX = this.getX();
        //
        //        return tmpC.subtract(tmpQ.multiply(tmpX));

        return this.getC();
    }

    final void handleSubsolution(final boolean solved, final PrimitiveDenseStore iterationSolution, final int[] included) {

        if (solved) {

            iterationSolution.fillMatching(iterationSolution, SUBTRACT, this.getX());

            if (this.isDebug()) {
                this.debug("Current: {}", this.getX().asList());
                this.debug("Step: {}", iterationSolution.copy().asList());
            }

            final double tmpNormCurrentX = this.getX().aggregateAll(Aggregator.NORM2);
            final double tmpNormStepX = iterationSolution.aggregateAll(Aggregator.NORM2);
            if (!options.solution.isSmall(tmpNormCurrentX, tmpNormStepX)) {
                // Non-zero solution

                double tmpStepLength = ONE;

                final int[] tmpExcluded = myActivator.getExcluded();
                if (tmpExcluded.length > 0) {

                    final MatrixStore<Double> tmpNumer = this.getSI(tmpExcluded);
                    final MatrixStore<Double> tmpDenom = this.getAI().builder().row(tmpExcluded).build().multiply(iterationSolution);

                    if (this.isDebug()) {
                        final PhysicalStore<Double> tmpStepLengths = tmpNumer.copy();
                        tmpStepLengths.fillMatching(tmpStepLengths, DIVIDE, tmpDenom);
                        this.debug("Looking for the largest possible step length (smallest positive scalar) among these: {}).", tmpStepLengths.asList());
                    }

                    for (int i = 0; i < tmpExcluded.length; i++) {

                        final double tmpN = tmpNumer.doubleValue(i); // Current slack
                        final double tmpD = tmpDenom.doubleValue(i); // Proposed slack change
                        final double tmpVal = options.slack.isSmall(tmpD, tmpN) ? ZERO : tmpN / tmpD;

                        if ((tmpD > ZERO) && (tmpVal >= ZERO) && (tmpVal < tmpStepLength) && !options.solution.isSmall(tmpNormStepX, tmpD)) {
                            tmpStepLength = tmpVal;
                            myConstraintToInclude = tmpExcluded[i];
                            if (this.isDebug()) {
                                this.debug("Best so far: {} @ {} ({}).", tmpStepLength, i, myConstraintToInclude);
                            }
                        }
                    }

                }

                if (tmpStepLength > ZERO) { // It is possible that it becomes == 0.0
                    this.getX().maxpy(tmpStepLength, iterationSolution);
                }

                this.setState(State.APPROXIMATE);

            } else if (this.isDebug()) {
                // Zero solution

                if (this.isDebug()) {
                    this.debug("Step too small!");
                }

                this.setState(State.FEASIBLE);
            }

        } else if (included.length >= 1) {
            // Subproblem NOT solved successfully
            // At least 1 active inequality

            this.shrink();

            this.performIteration();

        } else if (this.checkFeasibility(false)) {
            // Subproblem NOT solved successfully
            // No active inequality
            // Feasible current solution

            this.setState(State.FEASIBLE);

        } else {
            // Subproblem NOT solved successfully
            // No active inequality
            // Not feasible current solution

            this.setState(State.INFEASIBLE);
        }

        if (this.isDebug()) {
            this.debug("Post iteration");
            this.debug("\tSolution: {}", this.getX().copy().asList());
            if ((this.getAE() != null) && (this.getAE().count() > 0)) {
                this.debug("\tE-slack: {}", this.getSE().copy().asList());
            }
            if ((this.getAI() != null) && (this.getAI().count() > 0)) {
                if (included.length != 0) {
                    this.debug("\tI-included-slack: {}", this.getSI(included).copy().asList());
                }
                if (myActivator.getExcluded().length != 0) {
                    this.debug("\tI-excluded-slack: {}", this.getSI(myActivator.getExcluded()).copy().asList());
                }
            }
        }
    }

    abstract void initSolution(final MatrixStore<Double> tmpBI, final int tmpNumVars, final int tmpNumEqus);

    final void shrink() {

        final int[] tmpIncluded = myActivator.getIncluded();

        int tmpToExclude = tmpIncluded[0];
        double tmpMaxLagrange = ZERO;

        // final MatrixStore<Double> tmpLI = this.getLI(tmpIncluded);
        final MatrixStore<Double> tmpLI = myIterationL.builder().offsets(this.countEqualityConstraints(), 0).row(tmpIncluded).get();
        for (int i = 0; i < tmpIncluded.length; i++) {
            final double tmpVal = Math.abs(tmpLI.doubleValue(i));
            if (tmpVal >= tmpMaxLagrange) {
                tmpMaxLagrange = tmpVal;
                tmpToExclude = tmpIncluded[i];
            }
        }
        this.excludeAndRemove(tmpToExclude);
    }

    MatrixStore<Double> getIterationL(final int[] included) {

        final int tmpCountE = this.countEqualityConstraints();

        final MatrixStore<Double> tmpLI = myIterationL.builder().offsets(tmpCountE, 0).row(included).get();

        return myIterationL.builder().limits(tmpCountE, 1).below(tmpLI).get();
    }

}
