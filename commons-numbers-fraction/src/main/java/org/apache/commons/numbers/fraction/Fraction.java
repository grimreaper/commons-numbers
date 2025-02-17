/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.numbers.fraction;

import java.io.Serializable;
import org.apache.commons.numbers.core.ArithmeticUtils;

/**
 * Representation of a rational number.
 *
 * implements Serializable since 2.0
 */
public class Fraction
    extends Number
    implements Comparable<Fraction>, Serializable {

    /** A fraction representing "1". */
    public static final Fraction ONE = new Fraction(1, 1);

    /** A fraction representing "0". */
    public static final Fraction ZERO = new Fraction(0, 1);

    /** Serializable version identifier */
    private static final long serialVersionUID = 3698073679419233275L;

    /** Parameter name for fraction (to satisfy checkstyle). */
    private static final String PARAM_NAME_FRACTION = "fraction";

    /** The default epsilon used for convergence. */
    private static final double DEFAULT_EPSILON = 1e-5;
    
    /** The denominator. */
    private final int denominator;

    /** The numerator. */
    private final int numerator;

    /**
     * Create a fraction given the double value and either the maximum error
     * allowed or the maximum number of denominator digits.
     * <p>
     *
     * NOTE: This constructor is called with EITHER
     *   - a valid epsilon value and the maxDenominator set to Integer.MAX_VALUE
     *     (that way the maxDenominator has no effect).
     * OR
     *   - a valid maxDenominator value and the epsilon value set to zero
     *     (that way epsilon only has effect if there is an exact match before
     *     the maxDenominator value is reached).
     * </p><p>
     *
     * It has been done this way so that the same code can be (re)used for both
     * scenarios. However this could be confusing to users if it were part of
     * the public API and this constructor should therefore remain PRIVATE.
     * </p>
     *
     * See JIRA issue ticket MATH-181 for more details:
     *
     *     https://issues.apache.org/jira/browse/MATH-181
     *
     * @param value the double value to convert to a fraction.
     * @param epsilon maximum error allowed.  The resulting fraction is within
     *        {@code epsilon} of {@code value}, in absolute terms.
     * @param maxDenominator maximum denominator value allowed.
     * @param maxIterations maximum number of convergents
     * @throws IllegalArgumentException if the continued fraction failed to
     *         converge.
     */
    private Fraction(double value, double epsilon, int maxDenominator, int maxIterations)
    {
        long overflow = Integer.MAX_VALUE;
        double r0 = value;
        long a0 = (long)Math.floor(r0);
        if (Math.abs(a0) > overflow) {
            throw new FractionException(FractionException.ERROR_CONVERSION, value, a0, 1l);
        }

        // check for (almost) integer arguments, which should not go to iterations.
        if (Math.abs(a0 - value) < epsilon) {
            this.numerator = (int) a0;
            this.denominator = 1;
            return;
        }

        long p0 = 1;
        long q0 = 0;
        long p1 = a0;
        long q1 = 1;

        long p2 = 0;
        long q2 = 1;

        int n = 0;
        boolean stop = false;
        do {
            ++n;
            double r1 = 1.0 / (r0 - a0);
            long a1 = (long)Math.floor(r1);
            p2 = (a1 * p1) + p0;
            q2 = (a1 * q1) + q0;

            if ((Math.abs(p2) > overflow) || (Math.abs(q2) > overflow)) {
                // in maxDenominator mode, if the last fraction was very close to the actual value
                // q2 may overflow in the next iteration; in this case return the last one.
                if (epsilon == 0.0 && Math.abs(q1) < maxDenominator) {
                    break;
                }
                throw new FractionException(FractionException.ERROR_CONVERSION, value, p2, q2);
            }

            double convergent = (double)p2 / (double)q2;
            if (n < maxIterations && Math.abs(convergent - value) > epsilon && q2 < maxDenominator) {
                p0 = p1;
                p1 = p2;
                q0 = q1;
                q1 = q2;
                a0 = a1;
                r0 = r1;
            } else {
                stop = true;
            }
        } while (!stop);

        if (n >= maxIterations) {
            throw new FractionException(FractionException.ERROR_CONVERSION, value, maxIterations);
        }

        if (q2 < maxDenominator) {
            this.numerator = (int) p2;
            this.denominator = (int) q2;
        } else {
            this.numerator = (int) p1;
            this.denominator = (int) q1;
        }
    }
    
    /**
     * Private constructor for integer fractions.
     * @param num the numerator.
     * @param den the denominator.
     * @throws ArithmeticException if the denominator is {@code zero}
     *                             or if integer overflow occurs
     */
    private Fraction(int num, int den) {
        if (den == 0) {
            throw new ArithmeticException("division by zero");
        }
        if (den < 0) {
            if (num == Integer.MIN_VALUE ||
                den == Integer.MIN_VALUE) {
                throw new FractionException(FractionException.ERROR_NEGATION_OVERFLOW, num, den);
            }
            num = -num;
            den = -den;
        }
        // reduce numerator and denominator by greatest common denominator.
        final int d = ArithmeticUtils.gcd(num, den);
        if (d > 1) {
            num /= d;
            den /= d;
        }

        // move sign to numerator.
        if (den < 0) {
            num = -num;
            den = -den;
        }
        this.numerator   = num;
        this.denominator = den;
    }
    /**
     * Create a fraction given the double value.
     * @param value the double value to convert to a fraction.
     * @throws IllegalArgumentException if the continued fraction failed to
     *         converge.
     * @return {@link Fraction} instance
     */
    public static Fraction from(double value) {
        return from(value, DEFAULT_EPSILON, 100);
    }

    /**
     * Create a fraction given the double value and maximum error allowed.
     * <p>
     * References:
     * <ul>
     * <li><a href="http://mathworld.wolfram.com/ContinuedFraction.html">
     * Continued Fraction</a> equations (11) and (22)-(26)</li>
     * </ul>
     *
     * @param value the double value to convert to a fraction.
     * @param epsilon maximum error allowed.  The resulting fraction is within
     *        {@code epsilon} of {@code value}, in absolute terms.
     * @param maxIterations maximum number of convergents
     * @throws IllegalArgumentException if the continued fraction failed to
     *         converge.
     * @return {@link Fraction} instance
     */
    public static Fraction from(double value, double epsilon, int maxIterations)
    {
        return new Fraction(value, epsilon, Integer.MAX_VALUE, maxIterations);
    }

    /**
     * Create a fraction given the double value and maximum denominator.
     * <p>
     * References:
     * <ul>
     * <li><a href="http://mathworld.wolfram.com/ContinuedFraction.html">
     * Continued Fraction</a> equations (11) and (22)-(26)</li>
     * </ul>
     *
     * @param value the double value to convert to a fraction.
     * @param maxDenominator The maximum allowed value for denominator
     * @throws IllegalArgumentException if the continued fraction failed to
     *         converge
     * @return {@link Fraction} instance
     */
    public static Fraction from(double value, int maxDenominator)
    {
       return new Fraction(value, 0, maxDenominator, 100);
    }


    /**
     * Create a fraction from an int.
     * The fraction is num / 1.
     * @param num the numerator.
     * @return {@link Fraction} instance
     */
    public static Fraction of(int num) {
        return of(num, 1);
    }

    /**
     * Return a fraction given the numerator and denominator.  The fraction is
     * reduced to lowest terms.
     * @param num the numerator.
     * @param den the denominator.
     * @throws ArithmeticException if the denominator is {@code zero}
     *                             or if integer overflow occurs
     * @return {@link Fraction} instance
     */
    public static Fraction of(int num, int den) {
    	return new Fraction(num, den);
    }

    /**
     * Returns the absolute value of this fraction.
     * @return the absolute value.
     */
    public Fraction abs() {
        Fraction ret;
        if (numerator >= 0) {
            ret = this;
        } else {
            ret = negate();
        }
        return ret;
    }

    /**
     * Compares this object to another based on size.
     * @param object the object to compare to
     * @return -1 if this is less than {@code object}, +1 if this is greater
     *         than {@code object}, 0 if they are equal.
     */
    @Override
    public int compareTo(Fraction object) {
        long nOd = ((long) numerator) * object.denominator;
        long dOn = ((long) denominator) * object.numerator;
        return (nOd < dOn) ? -1 : ((nOd > dOn) ? +1 : 0);
    }

    /**
     * Gets the fraction as a {@code double}. This calculates the fraction as
     * the numerator divided by denominator.
     * @return the fraction as a {@code double}
     */
    @Override
    public double doubleValue() {
        return (double)numerator / (double)denominator;
    }

    /**
     * Test for the equality of two fractions.  If the lowest term
     * numerator and denominators are the same for both fractions, the two
     * fractions are considered to be equal.
     * @param other fraction to test for equality to this fraction
     * @return true if two fractions are equal, false if object is
     *         {@code null}, not an instance of {@link Fraction}, or not equal
     *         to this fraction instance.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof Fraction) {
            // since fractions are always in lowest terms, numerators and
            // denominators can be compared directly for equality.
            Fraction rhs = (Fraction)other;
            return (numerator == rhs.numerator) &&
                (denominator == rhs.denominator);
        }
        return false;
    }

    /**
     * Gets the fraction as a {@code float}. This calculates the fraction as
     * the numerator divided by denominator.
     * @return the fraction as a {@code float}
     */
    @Override
    public float floatValue() {
        return (float)doubleValue();
    }

    /**
     * Access the denominator.
     * @return the denominator.
     */
    public int getDenominator() {
        return denominator;
    }

    /**
     * Access the numerator.
     * @return the numerator.
     */
    public int getNumerator() {
        return numerator;
    }

    /**
     * Gets a hashCode for the fraction.
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        return 37 * (37 * 17 + numerator) + denominator;
    }

    /**
     * Gets the fraction as an {@code int}. This returns the whole number part
     * of the fraction.
     * @return the whole number fraction part
     */
    @Override
    public int intValue() {
        return (int)doubleValue();
    }

    /**
     * Gets the fraction as a {@code long}. This returns the whole number part
     * of the fraction.
     * @return the whole number fraction part
     */
    @Override
    public long longValue() {
        return (long)doubleValue();
    }

    /**
     * Return the additive inverse of this fraction.
     * @return the negation of this fraction.
     */
    public Fraction negate() {
        if (numerator==Integer.MIN_VALUE) {
            throw new FractionException(FractionException.ERROR_NEGATION_OVERFLOW, numerator, denominator);
        }
        return new Fraction(-numerator, denominator);
    }

    /**
     * Return the multiplicative inverse of this fraction.
     * @return the reciprocal fraction
     */
    public Fraction reciprocal() {
        return new Fraction(denominator, numerator);
    }

    /**
     * <p>Adds the value of this fraction to another, returning the result in reduced form.
     * The algorithm follows Knuth, 4.5.1.</p>
     *
     * @param fraction  the fraction to add, must not be {@code null}
     * @return a {@code Fraction} instance with the resulting values
     * @throws NullPointerException if the fraction is {@code null}
     * @throws ArithmeticException if the resulting numerator or denominator exceeds
     *  {@code Integer.MAX_VALUE}
     */
    public Fraction add(Fraction fraction) {
        return addSub(fraction, true /* add */);
    }

    /**
     * Add an integer to the fraction.
     * @param i the {@code integer} to add.
     * @return this + i
     */
    public Fraction add(final int i) {
        return new Fraction(numerator + i * denominator, denominator);
    }

    /**
     * <p>Subtracts the value of another fraction from the value of this one,
     * returning the result in reduced form.</p>
     *
     * @param fraction  the fraction to subtract, must not be {@code null}
     * @return a {@code Fraction} instance with the resulting values
     * @throws NullPointerException if the fraction is {@code null}
     * @throws ArithmeticException if the resulting numerator or denominator
     *   cannot be represented in an {@code int}.
     */
    public Fraction subtract(Fraction fraction) {
        return addSub(fraction, false /* subtract */);
    }

    /**
     * Subtract an integer from the fraction.
     * @param i the {@code integer} to subtract.
     * @return this - i
     */
    public Fraction subtract(final int i) {
        return new Fraction(numerator - i * denominator, denominator);
    }

    /**
     * Implement add and subtract. This algorithm is similar to that
     * described in Knuth 4.5.1. while making some concessions to 
     * performance. Note Knuth 4.5.1 Exercise 7, which observes that
     * adding two fractions with 32-bit numerators and denominators
     * requires 65 bits in extreme cases. Here calculations are performed 
     * with 64-bit longs and the BigFraction class is recommended for numbers
     * that may grow large enough to be in danger of overflow.
     *
     * @param fraction the fraction to subtract, must not be {@code null}
     * @param isAdd true to add, false to subtract
     * @return a {@code Fraction} instance with the resulting values
     * @throws NullPointerException if the fraction is {@code null}
     * @throws ArithmeticException if the resulting numerator or denominator
     *   cannot be represented in an {@code int}.
     */
    private Fraction addSub(Fraction fraction, boolean isAdd) {
        if (fraction == null) {
            throw new NullPointerException(PARAM_NAME_FRACTION);
        }
        // zero is identity for addition.
        if (numerator == 0) {
            return isAdd ? fraction : fraction.negate();
        }
        if (fraction.numerator == 0) {
            return this;
        }
        // t = u(v'/gcd) +/- v(u'/gcd)
        int d1 = ArithmeticUtils.gcd(denominator, fraction.denominator);
        int uvp = ArithmeticUtils.mulAndCheck(numerator, fraction.denominator / d1);
        int upv = ArithmeticUtils.mulAndCheck(fraction.numerator, denominator / d1);
        int t = isAdd ? ArithmeticUtils.addAndCheck(uvp, upv) : ArithmeticUtils.subAndCheck(uvp, upv);
        int tmodd1 = t % d1;
        int d2 = (tmodd1==0)?d1:ArithmeticUtils.gcd(tmodd1, d1);
        // result is (t/d2) / (u'/d1)(v'/d2)
        int w = t / d2;
        return new Fraction (w, ArithmeticUtils.mulAndCheck(denominator/d1,
                        fraction.denominator/d2));
    }

    /**
     * <p>Multiplies the value of this fraction by another, returning the
     * result in reduced form.</p>
     *
     * @param fraction  the fraction to multiply by, must not be {@code null}
     * @return a {@code Fraction} instance with the resulting values
     * @throws NullPointerException if the fraction is {@code null}
     * @throws ArithmeticException if the resulting numerator or denominator exceeds
     *  {@code Integer.MAX_VALUE}
     */
    public Fraction multiply(Fraction fraction) {
        if (fraction == null) {
            throw new NullPointerException(PARAM_NAME_FRACTION);
        }
        if (numerator == 0 || fraction.numerator == 0) {
            return ZERO;
        }
        // knuth 4.5.1
        // make sure we don't overflow unless the result *must* overflow.
        int d1 = ArithmeticUtils.gcd(numerator, fraction.denominator);
        int d2 = ArithmeticUtils.gcd(fraction.numerator, denominator);
        return getReducedFraction
        (ArithmeticUtils.mulAndCheck(numerator/d1, fraction.numerator/d2),
                ArithmeticUtils.mulAndCheck(denominator/d2, fraction.denominator/d1));
    }

    /**
     * Multiply the fraction by an integer.
     * @param i the {@code integer} to multiply by.
     * @return this * i
     */
    public Fraction multiply(final int i) {
        return multiply(of(i));
    }

    /**
     * <p>Divide the value of this fraction by another.</p>
     *
     * @param fraction  the fraction to divide by, must not be {@code null}
     * @return a {@code Fraction} instance with the resulting values
     * @throws ArithmeticException if the fraction to divide by is zero
     *                             or if the resulting numerator or denominator
     *                             exceeds {@code Integer.MAX_VALUE}
     */
    public Fraction divide(Fraction fraction) {
        if (fraction == null) {
            throw new NullPointerException(PARAM_NAME_FRACTION);
        }
        if (fraction.numerator == 0) {
            throw new FractionException("the fraction to divide by must not be zero: {0}/{1}",
                                        fraction.numerator, fraction.denominator);
        }
        return multiply(fraction.reciprocal());
    }

    /**
     * Divide the fraction by an integer.
     * @param i the {@code integer} to divide by.
     * @return this * i
     */
    public Fraction divide(final int i) {
        return divide(of(i));
    }

    /**
     * <p>Creates a {@code Fraction} instance with the 2 parts
     * of a fraction Y/Z.</p>
     *
     * <p>Any negative signs are resolved to be on the numerator.</p>
     *
     * @param numerator  the numerator, for example the three in 'three sevenths'
     * @param denominator  the denominator, for example the seven in 'three sevenths'
     * @return a new fraction instance, with the numerator and denominator reduced
     * @throws ArithmeticException if the denominator is {@code zero}
     */
    public static Fraction getReducedFraction(int numerator, int denominator) {
        if (denominator == 0) {
            throw new FractionException(FractionException.ERROR_ZERO_DENOMINATOR);
        }
        if (numerator==0) {
            return ZERO; // normalize zero.
        }
        // allow 2^k/-2^31 as a valid fraction (where k>0)
        if (denominator==Integer.MIN_VALUE && (numerator&1)==0) {
            numerator/=2; denominator/=2;
        }
        if (denominator < 0) {
            if (numerator==Integer.MIN_VALUE ||
                    denominator==Integer.MIN_VALUE) {
                throw new FractionException(FractionException.ERROR_NEGATION_OVERFLOW, numerator, denominator);
            }
            numerator = -numerator;
            denominator = -denominator;
        }
        // simplify fraction.
        int gcd = ArithmeticUtils.gcd(numerator, denominator);
        numerator /= gcd;
        denominator /= gcd;
        return new Fraction(numerator, denominator);
    }

    /**
     * @param n Power.
     * @return {@code this^n}
     */
    public Fraction pow(final int n) {
        if (n == 0) {
            return ONE;
        }
        if (numerator == 0) {
            return this;
        }

        return n < 0 ?
            new Fraction(ArithmeticUtils.pow(denominator, -n),
                         ArithmeticUtils.pow(numerator, -n)) :
            new Fraction(ArithmeticUtils.pow(numerator, n),
                         ArithmeticUtils.pow(denominator, n));
    }

    /**
     * <p>
     * Returns the {@code String} representing this fraction, ie
     * "num / dem" or just "num" if the denominator is one.
     * </p>
     *
     * @return a string representation of the fraction.
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final String str;
        if (denominator == 1) {
            str = Integer.toString(numerator);
        } else if (numerator == 0) {
            str = "0";
        } else {
            str = numerator + " / " + denominator;
        }
        return str;
    }
    
    /**
     * Parses a string that would be produced by {@link #toString()}
     * and instantiates the corresponding object.
     *
     * @param s String representation.
     * @return an instance.
     * @throws FractionException if the string does not
     * conform to the specification.
     */
    public static Fraction parse(String s) {
        final int slashLoc = s.indexOf("/");
        // if no slash, parse as single number
        if (slashLoc == -1) {
            return Fraction.of(Integer.parseInt(s.trim()));
        } else {
            final int num = Integer.parseInt(s.substring(0, slashLoc).trim());
            final int denom = Integer.parseInt(s.substring(slashLoc + 1).trim());
            return of(num, denom);
        }
    }

}
