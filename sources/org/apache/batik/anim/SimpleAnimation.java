/*

   Copyright 2006  The Apache Software Foundation 

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */
package org.apache.batik.anim;

import java.awt.geom.Point2D;

import org.apache.batik.anim.timing.TimedElement;
import org.apache.batik.anim.values.AnimatableValue;
import org.apache.batik.ext.awt.geom.Cubic;

/**
 * An animation class for 'animate' animations.
 *
 * @author <a href="mailto:cam%40mcc%2eid%2eau">Cameron McCormack</a>
 * @version $Id$
 */
public class SimpleAnimation extends AbstractAnimation {

    /**
     * The interpolation mode of this animator.  This should take one
     * of the CALC_MODE_* constants defined in {@link AbstractAnimation}.
     */
    protected int calcMode;

    /**
     * Values between which to interpolate.
     */
    protected AnimatableValue[] values;

    /**
     * Time values to control the pacing of the animation.
     */
    protected float[] keyTimes;

    /**
     * Bezier control points that control the pacing of the animation.
     */
    protected float[] keySplines;

    /**
     * Cubics built from the bezier control points in {@link #keySplines}.
     */
    protected Cubic[] keySplineCubics;

    /**
     * Starting value of the animation.
     */
    protected AnimatableValue from;

    /**
     * Ending value of the animation.
     */
    protected AnimatableValue to;

    /**
     * Relative offset value for the animation.
     */
    protected AnimatableValue by;
    
    /**
     * Whether this animation adds to ones below it in the animation sandwich
     * or replaces them.
     */
    protected boolean additive;

    /**
     * Whether this animation accumulates from previous iterations.
     */
    protected boolean cumulative;

    /**
     * Whether this is a 'to animation' (i.e., one specified by a "to"
     * but not a "from" value).
     */
    protected boolean toAnimation;

    /**
     * Creates a new SimpleAnimation.
     */
    public SimpleAnimation(TimedElement timedElement,
                           AnimatableElement animatableElement,
                           int calcMode,
                           float[] keyTimes,
                           float[] keySplines,
                           boolean additive,
                           boolean cumulative,
                           AnimatableValue[] values,
                           AnimatableValue from,
                           AnimatableValue to,
                           AnimatableValue by) {
        super(timedElement, animatableElement);

        toAnimation = false;
        if (values == null) {
            if (from != null) {
                values = new AnimatableValue[2];
                values[0] = from;
                if (to != null) {
                    values[1] = to;
                } else if (by != null) {
                    values[1] = from.interpolate(null, null, 0f, by, 1); 
                } else {
                    throw timedElement.createException
                        ("values.to.by.missing", new Object[] { null });
                }
            } else {
                if (to != null) {
                    values = new AnimatableValue[2];
                    values[0] = animatableElement.getUnderlyingValue();
                    values[1] = to;
                    toAnimation = true;
                    cumulative = false;
                } else if (by != null) {
                    additive = true;
                    values = new AnimatableValue[2];
                    values[0] = by.getZeroValue();
                    values[1] = by;
                } else {
                    throw timedElement.createException
                        ("values.to.by.missing", new Object[] { null });
                }
            }
        }

        if (keyTimes != null) {
            boolean invalidKeyTimes = false;
            if ((calcMode == CALC_MODE_LINEAR || calcMode == CALC_MODE_SPLINE)
                    && (keyTimes.length < 2
                        || keyTimes[0] != 0
                        || keyTimes[keyTimes.length - 1] != 1)
                    || calcMode == CALC_MODE_DISCRETE
                        && (keyTimes.length == 0 || keyTimes[0] != 0)
                    || keyTimes.length != values.length) {
                invalidKeyTimes = true;
            }
            if (!invalidKeyTimes) {
                for (int i = 1; i < keyTimes.length; i++) {
                    if (keyTimes[i] < 0 || keyTimes[1] > 1
                            || keyTimes[i] < keyTimes[i - 1]) {
                        invalidKeyTimes = true;
                        break;
                    }
                }
            }
            if (invalidKeyTimes) {
                throw timedElement.createException
                    ("attribute.malformed",
                     new Object[] { null,
                                    SMILConstants.SMIL_KEY_TIMES_ATTRIBUTE });
            }
        } else {
            if (calcMode == CALC_MODE_LINEAR || calcMode == CALC_MODE_SPLINE) {
                int count = values.length == 1 ? 2 : values.length;
                keyTimes = new float[count];
                for (int i = 0; i < count; i++) {
                    keyTimes[i] = (float) i / (count - 1);
                }
            } else if (calcMode == CALC_MODE_DISCRETE) {
                int count = values.length;
                keyTimes = new float[count];
                for (int i = 0; i < count; i++) {
                    keyTimes[i] = (float) i / count;
                }
            }
        }

        if (calcMode == CALC_MODE_SPLINE) {
            if (keySplines == null
                    || keySplines.length != (keyTimes.length - 1) * 4) {
                throw timedElement.createException
                    ("attribute.malformed",
                     new Object[] { null,
                                    SMILConstants.SMIL_KEY_SPLINES_ATTRIBUTE });
            }
            keySplineCubics = new Cubic[keyTimes.length - 1];
            for (int i = 0; i < keyTimes.length - 1; i++) {
                keySplineCubics[i] = new Cubic(0, 0,
                                               keySplines[i * 4],
                                               keySplines[i * 4 + 1],
                                               keySplines[i * 4 + 2],
                                               keySplines[i * 4 + 3],
                                               1, 1);
            }
        }

        this.calcMode = calcMode;
        this.keyTimes = keyTimes;
        this.keySplines = keySplines;
        this.additive = additive;
        this.cumulative = cumulative;
        this.values = values;
        this.from = from;
        this.to = to;
        this.by = by;
    }

    /**
     * Returns whether this animation will replace values on animations
     * lower in the sandwich.
     */
    protected boolean willReplace() {
        return !additive;
    }

    /**
     * Called when the element is sampled for its "last" value.
     */
    protected void sampledLastValue(int repeatIteration) {
        sampledAtUnitTime(1f, repeatIteration);
    }

    /**
     * Called when the element is sampled at the given time.
     */
    protected void sampledAt(float simpleTime, float simpleDur,
                             int repeatIteration) {
        float unitTime;
        if (simpleDur == TimedElement.INDEFINITE) {
            unitTime = 0;
        } else {
            unitTime = simpleTime / simpleDur;
        }
        sampledAtUnitTime(unitTime, repeatIteration);
    }

    /**
     * Called when the element is sampled at the given unit time.  This updates
     * the {@link #value} of the animation if active.
     */
    protected void sampledAtUnitTime(float unitTime, int repeatIteration) {
        AnimatableValue value, accumulation, nextValue;
        float interpolation = 0;
        if (calcMode != CALC_MODE_PACED) {
            if (unitTime != 1) {
                int keyTimeIndex = 0;
                while (keyTimeIndex < keyTimes.length - 1
                        && unitTime >= keyTimes[keyTimeIndex + 1]) {
                    keyTimeIndex++;
                }
                value = values[keyTimeIndex];
                if (calcMode == CALC_MODE_LINEAR
                        || calcMode == CALC_MODE_SPLINE) {
                    nextValue = values[keyTimeIndex + 1];
                    interpolation = (unitTime - keyTimes[keyTimeIndex])
                        / (keyTimes[keyTimeIndex + 1] - keyTimes[keyTimeIndex]);
                    if (calcMode == CALC_MODE_SPLINE && unitTime != 0) {
                        // XXX This could be done better, e.g. with
                        //     Newton-Raphson.
                        Cubic c = keySplineCubics[keyTimeIndex];
                        float tolerance = 0.001f;
                        float min = 0;
                        float max = 1;
                        Point2D.Double p;
                        for (;;) {
                            float t = (min + max) / 2;
                            p = c.eval(t);
                            double x = p.getX();
                            if (Math.abs(x - interpolation) < tolerance) {
                                break;
                            }
                            if (x < interpolation) {
                                min = t;
                            } else {
                                max = t;
                            }
                        }
                        interpolation = (float) p.getY();
                    }
                } else {
                    nextValue = null;
                }
            } else {
                value = values[values.length - 1];
                nextValue = null;
            }
            if (cumulative) {
                accumulation = values[values.length - 1];
            } else {
                accumulation = null;
            }
        } else {
            // XXX Handle paced animation.
            value = null;
            nextValue = null;
            accumulation = null;
        }

        this.value = value.interpolate(this.value, nextValue, interpolation,
                                       accumulation, repeatIteration);
        if (this.value.hasChanged()) {
            markDirty();
        }
    }
}