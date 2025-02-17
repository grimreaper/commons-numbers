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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import org.apache.commons.numbers.core.ArithmeticUtils;

/**
 * Representation of a rational number without any overflow. This class is
 * immutable.
 */
public class BigFraction extends Number implements Comparable<BigFraction>, Serializable {    
    /** A fraction representing "0". */
    public static final BigFraction ZERO = new BigFraction(0);

    /** A fraction representing "1". */
    public static final BigFraction ONE = new BigFraction(1);

    /** Serializable version identifier. */
    private static final long serialVersionUID = -5630213147331578515L;

    /** Parameter name for fraction (to satisfy checkstyle). */
    private static final String PARAM_NAME_FRACTION = "fraction";

    /** Parameter name for BigIntegers (to satisfy checkstyle). */
    private static final String PARAM_NAME_BG = "bg";

    /** The numerator. */
    private final BigInteger numerator;

    /** The denominator. */
    private final BigInteger denominator;

    /**
     * Private constructor for BigFraction ofInt() factory methods.
     *
     * @param num the numerator, must not be {@code null}.
     * @param den the denominator, must not be {@code null}.
     * @throws ArithmeticException if the denominator is zero.
     */
    private BigFraction(BigInteger num, BigInteger den) {
        checkNotNull(num, "numerator");
        checkNotNull(den, "denominator");
        if (den.signum() == 0) {
            throw new FractionException(FractionException.ERROR_ZERO_DENOMINATOR);
        }
        if (num.signum() == 0) {
            numerator   = BigInteger.ZERO;
            denominator = BigInteger.ONE;
        } else {

            // reduce numerator and denominator by greatest common denominator
            final BigInteger gcd = num.gcd(den);
            if (BigInteger.ONE.compareTo(gcd) < 0) {
                num = num.divide(gcd);
                den = den.divide(gcd);
            }

            // move sign to numerator
            if (den.signum() == -1) {
                num = num.negate();
                den = den.negate();
            }

            // store the values in the final fields
            numerator   = num;
            denominator = den;

        }
    }

    /**
     * Create a fraction given the double value and either the maximum error
     * allowed or the maximum number of denominator digits.
     * <p>
     *
     * NOTE: This constructor is called with EITHER - a valid epsilon value and
     * the maxDenominator set to Integer.MAX_VALUE (that way the maxDenominator
     * has no effect). OR - a valid maxDenominator value and the epsilon value
     * set to zero (that way epsilon only has effect if there is an exact match
     * before the maxDenominator value is reached).
     * </p>
     * <p>
     *
     * It has been done this way so that the same code can be (re)used for both
     * scenarios. However this could be confusing to users if it were part of
     * the public API and this constructor should therefore remain PRIVATE.
     * </p>
     *
     * See JIRA issue ticket MATH-181 for more details:
     *
     * https://issues.apache.org/jira/browse/MATH-181
     *
     * @param value
     *            the double value to convert to a fraction.
     * @param epsilon
     *            maximum error allowed. The resulting fraction is within
     *            <code>epsilon</code> of <code>value</code>, in absolute terms.
     * @param maxDenominator
     *            maximum denominator value allowed.
     * @param maxIterations
     *            maximum number of convergents.
     * @throws ArithmeticException
     *             if the continued fraction failed to converge.
     */
    private BigFraction(final double value, final double epsilon,
                        final int maxDenominator, int maxIterations) {
        long overflow = Integer.MAX_VALUE;
        double r0 = value;
        long a0 = (long) Math.floor(r0);

        if (Math.abs(a0) > overflow) {
            throw new FractionException(FractionException.ERROR_CONVERSION_OVERFLOW, value, a0, 1l);
        }

        // check for (almost) integer arguments, which should not go
        // to iterations.
        if (Math.abs(a0 - value) < epsilon) {
            numerator = BigInteger.valueOf(a0);
            denominator = BigInteger.ONE;
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
            final double r1 = 1.0 / (r0 - a0);
            final long a1 = (long) Math.floor(r1);
            p2 = (a1 * p1) + p0;
            q2 = (a1 * q1) + q0;
            if ((p2 > overflow) || (q2 > overflow)) {
                // in maxDenominator mode, if the last fraction was very close to the actual value
                // q2 may overflow in the next iteration; in this case return the last one.
                if (epsilon == 0.0 && Math.abs(q1) < maxDenominator) {
                    break;
                }
                throw new FractionException(FractionException.ERROR_CONVERSION_OVERFLOW, value, p2, q2);
            }

            final double convergent = (double) p2 / (double) q2;
            if ((n < maxIterations) &&
                (Math.abs(convergent - value) > epsilon) &&
                (q2 < maxDenominator)) {
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
            numerator   = BigInteger.valueOf(p2);
            denominator = BigInteger.valueOf(q2);
        } else {
            numerator   = BigInteger.valueOf(p1);
            denominator = BigInteger.valueOf(q1);
        }
    }
    
    /**
     * Create a fraction given the double value.
     * <p>
     * This constructor behaves <em>differently</em> from
     * {@link #BigFraction(double, double, int)}. It converts the double value
     * exactly, considering its internal bits representation. This works for all
     * values except NaN and infinities and does not requires any loop or
     * convergence threshold.
     * </p>
     * <p>
     * Since this conversion is exact and since double numbers are sometimes
     * approximated, the fraction created may seem strange in some cases. For example,
     * calling <code>new BigFraction(1.0 / 3.0)</code> does <em>not</em> create
     * the fraction 1/3, but the fraction 6004799503160661 / 18014398509481984
     * because the double number passed to the constructor is not exactly 1/3
     * (this number cannot be stored exactly in IEEE754).
     * </p>
     * @see #BigFraction(double, double, int)
     * @param value the double value to convert to a fraction.
     * @exception IllegalArgumentException if value is NaN or infinite
     */
    private BigFraction(final double value) throws IllegalArgumentException {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("cannot convert NaN value");
        }
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("cannot convert infinite value");
        }

        // compute m and k such that value = m * 2^k
        final long bits     = Double.doubleToLongBits(value);
        final long sign     = bits & 0x8000000000000000L;
        final long exponent = bits & 0x7ff0000000000000L;
        long m              = bits & 0x000fffffffffffffL;
        if (exponent != 0) {
            // this was a normalized number, add the implicit most significant bit
            m |= 0x0010000000000000L;
        }
        if (sign != 0) {
            m = -m;
        }
        int k = ((int) (exponent >> 52)) - 1075;
        while (((m & 0x001ffffffffffffeL) != 0) && ((m & 0x1) == 0)) {
            m >>= 1;
            ++k;
        }

        if (k < 0) {
            numerator   = BigInteger.valueOf(m);
            denominator = BigInteger.ZERO.flipBit(-k);
        } else {
            numerator   = BigInteger.valueOf(m).multiply(BigInteger.ZERO.flipBit(k));
            denominator = BigInteger.ONE;
        }

    }

    /**
     * <p>
     * Create a {@link BigFraction} equivalent to the passed {@code BigInteger}, ie
     * "num / 1".
     * </p>
     *
     * @param num the numerator.
     * @return {@link BigFraction instance
     */
    public static BigFraction of(final BigInteger num) {
        return new BigFraction(num, BigInteger.ONE);
    }

    /**
     * Create a {@link BigFraction} given the numerator and denominator as
     * {@code BigInteger}. The {@link BigFraction} is reduced to lowest terms.
     *
     * @param num the numerator, must not be {@code null}.
     * @param den the denominator, must not be {@code null}.
     * @throws ArithmeticException if the denominator is zero.
     * @return {@link BigFraction instance
     */
    public static BigFraction of(BigInteger num, BigInteger den) {
    	return new BigFraction(num, den);
    }

    /**
     * Create a fraction given the double value.
     * <p>
     * This factory method behaves <em>differently</em> from
     * {@link #from(double, double, int)}. It converts the double value
     * exactly, considering its internal bits representation. This works for all
     * values except NaN and infinities and does not requires any loop or
     * convergence threshold.
     * </p>
     * <p>
     * Since this conversion is exact and since double numbers are sometimes
     * approximated, the fraction created may seem strange in some cases. For example,
     * calling <code>new BigFraction(1.0 / 3.0)</code> does <em>not</em> create
     * the fraction 1/3, but the fraction 6004799503160661 / 18014398509481984
     * because the double number passed to the constructor is not exactly 1/3
     * (this number cannot be stored exactly in IEEE754).
     * </p>
     * @see #BigFraction(double, double, int)
     * @param value the double value to convert to a fraction.
     * @exception IllegalArgumentException if value is NaN or infinite
     * @return {@link BigFraction instance
     */
    public static BigFraction from(final double value) throws IllegalArgumentException {
    	return new BigFraction(value);
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
     * @param value
     *            the double value to convert to a fraction.
     * @param epsilon
     *            maximum error allowed. The resulting fraction is within
     *            <code>epsilon</code> of <code>value</code>, in absolute terms.
     * @param maxIterations
     *            maximum number of convergents.
     * @throws ArithmeticException
     *             if the continued fraction failed to converge.
     * @see #BigFraction(double)
     * @return {@link BigFraction instance
     */
    public static BigFraction from(final double value, final double epsilon,
                       final int maxIterations) {
        return new BigFraction(value, epsilon, Integer.MAX_VALUE, maxIterations);
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
     * @param value
     *            the double value to convert to a fraction.
     * @param maxDenominator
     *            The maximum allowed value for denominator.
     * @throws ArithmeticException
     *             if the continued fraction failed to converge.
     * @return {@link BigFraction instance
     */
    public static BigFraction from(final double value, final int maxDenominator) {
        return new BigFraction(value, 0, maxDenominator, 100);
    }

    /**
     * <p>
     * Create a {@link BigFraction} equivalent to the passed {@code int}, ie
     * "num / 1".
     * </p>
     *
     * @param num
     *            the numerator.
     * @return {@link BigFraction instance
     */
    public static BigFraction of(final int num) {
        return new BigFraction(BigInteger.valueOf(num), BigInteger.ONE);
    }

    /**
     * <p>
     * Create a {@link BigFraction} given the numerator and denominator as simple
     * {@code int}. The {@link BigFraction} is reduced to lowest terms.
     * </p>
     *
     * @param num the numerator.
     * @param den the denominator.
     * @return {@link BigFraction instance
     */
    public static BigFraction of(final int num, final int den) {
        return new BigFraction(BigInteger.valueOf(num), BigInteger.valueOf(den));
    }

    /**
     * <p>
     * Create a {@link BigFraction} equivalent to the passed long, ie "num / 1".
     * </p>
     *
     * @param num the numerator.
     * @return {@link BigFraction instance
     */
    public static BigFraction of(final long num) {
        return new BigFraction(BigInteger.valueOf(num), BigInteger.ONE);
    }

    /**
     * <p>
     * Create a {@link BigFraction} given the numerator and denominator as simple
     * {@code long}. The {@link BigFraction} is reduced to lowest terms.
     * </p>
     *
     * @param num the numerator.
     * @param den the denominator.
     * @return {@link BigFraction instance
     */
    public static BigFraction of(final long num, final long den) {
        return new BigFraction(BigInteger.valueOf(num), BigInteger.valueOf(den));
    }

    /**
     * <p>
     * Creates a <code>BigFraction</code> instance with the 2 parts of a fraction
     * Y/Z.
     * </p>
     *
     * <p>
     * Any negative signs are resolved to be on the numerator.
     * </p>
     *
     * @param numerator
     *            the numerator, for example the three in 'three sevenths'.
     * @param denominator
     *            the denominator, for example the seven in 'three sevenths'.
     * @return a new fraction instance, with the numerator and denominator
     *         reduced.
     * @throws ArithmeticException
     *             if the denominator is <code>zero</code>.
     */
    public static BigFraction getReducedFraction(final int numerator,
                                                 final int denominator) {
        if (numerator == 0) {
            return ZERO; // normalize zero.
        }

        return of(numerator, denominator);
    }

    /**
     * <p>
     * Returns the absolute value of this {@link BigFraction}.
     * </p>
     *
     * @return the absolute value as a {@link BigFraction}.
     */
    public BigFraction abs() {
        return (numerator.signum() == 1) ? this : negate();
    }

    /**
     * <p>
     * Adds the value of this fraction to the passed {@link BigInteger},
     * returning the result in reduced form.
     * </p>
     *
     * @param bg
     *            the {@link BigInteger} to add, must'nt be <code>null</code>.
     * @return a <code>BigFraction</code> instance with the resulting values.
     */
    public BigFraction add(final BigInteger bg) {
        checkNotNull(bg, PARAM_NAME_BG);

        if (numerator.signum() == 0) {
            return of(bg);
        }
        if (bg.signum() == 0) {
            return this;
        }

        return new BigFraction(numerator.add(denominator.multiply(bg)), denominator);
    }

    /**
     * <p>
     * Adds the value of this fraction to the passed {@code integer}, returning
     * the result in reduced form.
     * </p>
     *
     * @param i
     *            the {@code integer} to add.
     * @return a <code>BigFraction</code> instance with the resulting values.
     */
    public BigFraction add(final int i) {
        return add(BigInteger.valueOf(i));
    }

    /**
     * <p>
     * Adds the value of this fraction to the passed {@code long}, returning
     * the result in reduced form.
     * </p>
     *
     * @param l
     *            the {@code long} to add.
     * @return a <code>BigFraction</code> instance with the resulting values.
     */
    public BigFraction add(final long l) {
        return add(BigInteger.valueOf(l));
    }

    /**
     * <p>
     * Adds the value of this fraction to another, returning the result in
     * reduced form.
     * </p>
     *
     * @param fraction
     *            the {@link BigFraction} to add, must not be <code>null</code>.
     * @return a {@link BigFraction} instance with the resulting values.
     */
    public BigFraction add(final BigFraction fraction) {
        checkNotNull(fraction, PARAM_NAME_FRACTION);
        if (fraction.numerator.signum() == 0) {
            return this;
        }
        if (numerator.signum() == 0) {
            return fraction;
        }

        final BigInteger num;
        final BigInteger den;

        if (denominator.equals(fraction.denominator)) {
            num = numerator.add(fraction.numerator);
            den = denominator;
        } else {
            num = (numerator.multiply(fraction.denominator)).add((fraction.numerator).multiply(denominator));
            den = denominator.multiply(fraction.denominator);
        }

        if (num.signum() == 0) {
            return ZERO;
        }

        return new BigFraction(num, den);

    }

    /**
     * <p>
     * Gets the fraction as a <code>BigDecimal</code>. This calculates the
     * fraction as the numerator divided by denominator.
     * </p>
     *
     * @return the fraction as a <code>BigDecimal</code>.
     * @throws ArithmeticException
     *             if the exact quotient does not have a terminating decimal
     *             expansion.
     * @see BigDecimal
     */
    public BigDecimal bigDecimalValue() {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator));
    }

    /**
     * <p>
     * Gets the fraction as a <code>BigDecimal</code> following the passed
     * rounding mode. This calculates the fraction as the numerator divided by
     * denominator.
     * </p>
     *
     * @param roundingMode Rounding mode to apply.
     * @return the fraction as a <code>BigDecimal</code>.
     * @see BigDecimal
     */
    public BigDecimal bigDecimalValue(RoundingMode roundingMode) {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), roundingMode);
    }

    /**
     * <p>
     * Gets the fraction as a <code>BigDecimal</code> following the passed scale
     * and rounding mode. This calculates the fraction as the numerator divided
     * by denominator.
     * </p>
     *
     * @param scale
     *            scale of the <code>BigDecimal</code> quotient to be returned.
     *            see {@link BigDecimal} for more information.
     * @param roundingMode Rounding mode to apply.
     * @return the fraction as a <code>BigDecimal</code>.
     * @see BigDecimal
     */
    public BigDecimal bigDecimalValue(final int scale, RoundingMode roundingMode) {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), scale, roundingMode);
    }

    /**
     * <p>
     * Compares this object to another based on size.
     * </p>
     *
     * @param object
     *            the object to compare to, must not be <code>null</code>.
     * @return -1 if this is less than {@code object}, +1 if this is greater
     *         than {@code object}, 0 if they are equal.
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final BigFraction object) {
        int lhsSigNum = numerator.signum();
        int rhsSigNum = object.numerator.signum();

        if (lhsSigNum != rhsSigNum) {
            return (lhsSigNum > rhsSigNum) ? 1 : -1;
        }
        if (lhsSigNum == 0) {
            return 0;
        }

        BigInteger nOd = numerator.multiply(object.denominator);
        BigInteger dOn = denominator.multiply(object.numerator);
        return nOd.compareTo(dOn);
    }

    /**
     * <p>
     * Divide the value of this fraction by the passed {@code BigInteger},
     * ie {@code this * 1 / bg}, returning the result in reduced form.
     * </p>
     *
     * @param bg the {@code BigInteger} to divide by, must not be {@code null}
     * @return a {@link BigFraction} instance with the resulting values
     * @throws ArithmeticException if the fraction to divide by is zero
     */
    public BigFraction divide(final BigInteger bg) {
        checkNotNull(bg, PARAM_NAME_BG);
        if (bg.signum() == 0) {
            throw new FractionException(FractionException.ERROR_ZERO_DENOMINATOR);
        }
        if (numerator.signum() == 0) {
            return ZERO;
        }
        return new BigFraction(numerator, denominator.multiply(bg));
    }

    /**
     * <p>
     * Divide the value of this fraction by the passed {@code int}, ie
     * {@code this * 1 / i}, returning the result in reduced form.
     * </p>
     *
     * @param i the {@code int} to divide by
     * @return a {@link BigFraction} instance with the resulting values
     * @throws ArithmeticException if the fraction to divide by is zero
     */
    public BigFraction divide(final int i) {
        return divide(BigInteger.valueOf(i));
    }

    /**
     * <p>
     * Divide the value of this fraction by the passed {@code long}, ie
     * {@code this * 1 / l}, returning the result in reduced form.
     * </p>
     *
     * @param l the {@code long} to divide by
     * @return a {@link BigFraction} instance with the resulting values
     * @throws ArithmeticException if the fraction to divide by is zero
     */
    public BigFraction divide(final long l) {
        return divide(BigInteger.valueOf(l));
    }

    /**
     * <p>
     * Divide the value of this fraction by another, returning the result in
     * reduced form.
     * </p>
     *
     * @param fraction Fraction to divide by, must not be {@code null}.
     * @return a {@link BigFraction} instance with the resulting values.
     * @throws ArithmeticException if the fraction to divide by is zero
     */
    public BigFraction divide(final BigFraction fraction) {
        checkNotNull(fraction, PARAM_NAME_FRACTION);
        if (fraction.numerator.signum() == 0) {
            throw new FractionException(FractionException.ERROR_ZERO_DENOMINATOR);
        }
        if (numerator.signum() == 0) {
            return ZERO;
        }

        return multiply(fraction.reciprocal());
    }

    /**
     * <p>
     * Gets the fraction as a {@code double}. This calculates the fraction as
     * the numerator divided by denominator.
     * </p>
     *
     * @return the fraction as a {@code double}
     * @see java.lang.Number#doubleValue()
     */
    @Override
    public double doubleValue() {
        double doubleNum = numerator.doubleValue();
        double doubleDen = denominator.doubleValue();
        double result = doubleNum / doubleDen;
        if (Double.isInfinite(doubleNum) ||
            Double.isInfinite(doubleDen) ||
            Double.isNaN(result)) {
            // Numerator and/or denominator must be out of range:
            // Calculate how far to shift them to put them in range.
            int shift = Math.max(numerator.bitLength(),
                                 denominator.bitLength()) - Math.getExponent(Double.MAX_VALUE);
            result = numerator.shiftRight(shift).doubleValue() /
                denominator.shiftRight(shift).doubleValue();
        }
        return result;
    }

    /**
     * <p>
     * Test for the equality of two fractions. If the lowest term numerator and
     * denominators are the same for both fractions, the two fractions are
     * considered to be equal.
     * </p>
     *
     * @param other
     *            fraction to test for equality to this fraction, can be
     *            <code>null</code>.
     * @return true if two fractions are equal, false if object is
     *         <code>null</code>, not an instance of {@link BigFraction}, or not
     *         equal to this fraction instance.
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object other) {
        boolean ret = false;

        if (this == other) {
            ret = true;
        } else if (other instanceof BigFraction) {
            BigFraction rhs = ((BigFraction) other).reduce();
            BigFraction thisOne = this.reduce();
            ret = thisOne.numerator.equals(rhs.numerator) && thisOne.denominator.equals(rhs.denominator);
        }

        return ret;
    }

    /**
     * <p>
     * Gets the fraction as a {@code float}. This calculates the fraction as
     * the numerator divided by denominator.
     * </p>
     *
     * @return the fraction as a {@code float}.
     * @see java.lang.Number#floatValue()
     */
    @Override
    public float floatValue() {
        float floatNum = numerator.floatValue();
        float floatDen = denominator.floatValue();
        float result = floatNum / floatDen;
        if (Float.isInfinite(floatNum) ||
            Float.isInfinite(floatDen) ||
            Float.isNaN(result)) {
            // Numerator and/or denominator must be out of range:
            // Calculate how far to shift them to put them in range.
            int shift = Math.max(numerator.bitLength(),
                                 denominator.bitLength()) - Math.getExponent(Float.MAX_VALUE);
            result = numerator.shiftRight(shift).floatValue() /
                denominator.shiftRight(shift).floatValue();
        }
        return result;
    }

    /**
     * <p>
     * Access the denominator as a <code>BigInteger</code>.
     * </p>
     *
     * @return the denominator as a <code>BigInteger</code>.
     */
    public BigInteger getDenominator() {
        return denominator;
    }

    /**
     * <p>
     * Access the denominator as a {@code int}.
     * </p>
     *
     * @return the denominator as a {@code int}.
     */
    public int getDenominatorAsInt() {
        return denominator.intValue();
    }

    /**
     * <p>
     * Access the denominator as a {@code long}.
     * </p>
     *
     * @return the denominator as a {@code long}.
     */
    public long getDenominatorAsLong() {
        return denominator.longValue();
    }

    /**
     * <p>
     * Access the numerator as a <code>BigInteger</code>.
     * </p>
     *
     * @return the numerator as a <code>BigInteger</code>.
     */
    public BigInteger getNumerator() {
        return numerator;
    }

    /**
     * <p>
     * Access the numerator as a {@code int}.
     * </p>
     *
     * @return the numerator as a {@code int}.
     */
    public int getNumeratorAsInt() {
        return numerator.intValue();
    }

    /**
     * <p>
     * Access the numerator as a {@code long}.
     * </p>
     *
     * @return the numerator as a {@code long}.
     */
    public long getNumeratorAsLong() {
        return numerator.longValue();
    }

    /**
     * <p>
     * Gets a hashCode for the fraction.
     * </p>
     *
     * @return a hash code value for this object.
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return 37 * (37 * 17 + numerator.hashCode()) + denominator.hashCode();
    }

    /**
     * <p>
     * Gets the fraction as an {@code int}. This returns the whole number part
     * of the fraction.
     * </p>
     *
     * @return the whole number fraction part.
     * @see java.lang.Number#intValue()
     */
    @Override
    public int intValue() {
        return numerator.divide(denominator).intValue();
    }

    /**
     * <p>
     * Gets the fraction as a {@code long}. This returns the whole number part
     * of the fraction.
     * </p>
     *
     * @return the whole number fraction part.
     * @see java.lang.Number#longValue()
     */
    @Override
    public long longValue() {
        return numerator.divide(denominator).longValue();
    }

    /**
     * <p>
     * Multiplies the value of this fraction by the passed
     * <code>BigInteger</code>, returning the result in reduced form.
     * </p>
     *
     * @param bg the {@code BigInteger} to multiply by.
     * @return a {@code BigFraction} instance with the resulting values.
     */
    public BigFraction multiply(final BigInteger bg) {
        checkNotNull(bg, PARAM_NAME_BG);
        if (numerator.signum() == 0 || bg.signum() == 0) {
            return ZERO;
        }
        return new BigFraction(bg.multiply(numerator), denominator);
    }

    /**
     * <p>
     * Multiply the value of this fraction by the passed {@code int}, returning
     * the result in reduced form.
     * </p>
     *
     * @param i
     *            the {@code int} to multiply by.
     * @return a {@link BigFraction} instance with the resulting values.
     */
    public BigFraction multiply(final int i) {
        if (i == 0 || numerator.signum() == 0) {
            return ZERO;
        }

        return multiply(BigInteger.valueOf(i));
    }

    /**
     * <p>
     * Multiply the value of this fraction by the passed {@code long},
     * returning the result in reduced form.
     * </p>
     *
     * @param l
     *            the {@code long} to multiply by.
     * @return a {@link BigFraction} instance with the resulting values.
     */
    public BigFraction multiply(final long l) {
        if (l == 0 || numerator.signum() == 0) {
            return ZERO;
        }

        return multiply(BigInteger.valueOf(l));
    }

    /**
     * <p>
     * Multiplies the value of this fraction by another, returning the result in
     * reduced form.
     * </p>
     *
     * @param fraction Fraction to multiply by, must not be {@code null}.
     * @return a {@link BigFraction} instance with the resulting values.
     */
    public BigFraction multiply(final BigFraction fraction) {
        checkNotNull(fraction, PARAM_NAME_FRACTION);
        if (numerator.signum() == 0 ||
            fraction.numerator.signum() == 0) {
            return ZERO;
        }
        return new BigFraction(numerator.multiply(fraction.numerator),
                               denominator.multiply(fraction.denominator));
    }

    /**
     * <p>
     * Return the additive inverse of this fraction, returning the result in
     * reduced form.
     * </p>
     *
     * @return the negation of this fraction.
     */
    public BigFraction negate() {
        return new BigFraction(numerator.negate(), denominator);
    }

    /**
     * <p>
     * Returns a {@code BigFraction} whose value is
     * {@code (this<sup>exponent</sup>)}, returning the result in reduced form.
     * </p>
     *
     * @param exponent
     *            exponent to which this {@code BigFraction} is to be
     *            raised.
     * @return <tt>this<sup>exponent</sup></tt>.
     */
    public BigFraction pow(final int exponent) {
        if (exponent == 0) {
            return ONE;
        }
        if (numerator.signum() == 0) {
            return this;
        }

        if (exponent < 0) {
            return new BigFraction(denominator.pow(-exponent), numerator.pow(-exponent));
        }
        return new BigFraction(numerator.pow(exponent), denominator.pow(exponent));
    }

    /**
     * <p>
     * Returns a <code>BigFraction</code> whose value is
     * <tt>(this<sup>exponent</sup>)</tt>, returning the result in reduced form.
     * </p>
     *
     * @param exponent
     *            exponent to which this <code>BigFraction</code> is to be raised.
     * @return <tt>this<sup>exponent</sup></tt> as a <code>BigFraction</code>.
     */
    public BigFraction pow(final long exponent) {
        if (exponent == 0) {
            return ONE;
        }
        if (numerator.signum() == 0) {
            return this;
        }

        if (exponent < 0) {
            return new BigFraction(ArithmeticUtils.pow(denominator, -exponent),
                                   ArithmeticUtils.pow(numerator,   -exponent));
        }
        return new BigFraction(ArithmeticUtils.pow(numerator,   exponent),
                               ArithmeticUtils.pow(denominator, exponent));
    }

    /**
     * <p>
     * Returns a <code>BigFraction</code> whose value is
     * <tt>(this<sup>exponent</sup>)</tt>, returning the result in reduced form.
     * </p>
     *
     * @param exponent
     *            exponent to which this <code>BigFraction</code> is to be raised.
     * @return <tt>this<sup>exponent</sup></tt> as a <code>BigFraction</code>.
     */
    public BigFraction pow(final BigInteger exponent) {
        if (exponent.signum() == 0) {
            return ONE;
        }
        if (numerator.signum() == 0) {
            return this;
        }

        if (exponent.signum() == -1) {
            final BigInteger eNeg = exponent.negate();
            return new BigFraction(ArithmeticUtils.pow(denominator, eNeg),
                                   ArithmeticUtils.pow(numerator,   eNeg));
        }
        return new BigFraction(ArithmeticUtils.pow(numerator,   exponent),
                               ArithmeticUtils.pow(denominator, exponent));
    }

    /**
     * <p>
     * Returns a <code>double</code> whose value is
     * <tt>(this<sup>exponent</sup>)</tt>, returning the result in reduced form.
     * </p>
     *
     * @param exponent
     *            exponent to which this <code>BigFraction</code> is to be raised.
     * @return <tt>this<sup>exponent</sup></tt>.
     */
    public double pow(final double exponent) {
        return Math.pow(numerator.doubleValue(),   exponent) /
               Math.pow(denominator.doubleValue(), exponent);
    }

    /**
     * <p>
     * Return the multiplicative inverse of this fraction.
     * </p>
     *
     * @return the reciprocal fraction.
     */
    public BigFraction reciprocal() {
        return new BigFraction(denominator, numerator);
    }

    /**
     * <p>
     * Reduce this <code>BigFraction</code> to its lowest terms.
     * </p>
     *
     * @return the reduced <code>BigFraction</code>. It doesn't change anything if
     *         the fraction can be reduced.
     */
    public BigFraction reduce() {
        final BigInteger gcd = numerator.gcd(denominator);

        if (BigInteger.ONE.compareTo(gcd) < 0) {
            return new BigFraction(numerator.divide(gcd), denominator.divide(gcd));
        } else {
            return this;
        }
    }

    /**
     * <p>
     * Subtracts the value of an {@link BigInteger} from the value of this
     * {@code BigFraction}, returning the result in reduced form.
     * </p>
     *
     * @param bg the {@link BigInteger} to subtract, cannot be {@code null}.
     * @return a {@code BigFraction} instance with the resulting values.
     */
    public BigFraction subtract(final BigInteger bg) {
        checkNotNull(bg, PARAM_NAME_BG);
        if (bg.signum() == 0) {
            return this;
        }
        if (numerator.signum() == 0) {
            return of(bg.negate());
        }

        return new BigFraction(numerator.subtract(denominator.multiply(bg)), denominator);
    }

    /**
     * <p>
     * Subtracts the value of an {@code integer} from the value of this
     * {@code BigFraction}, returning the result in reduced form.
     * </p>
     *
     * @param i the {@code integer} to subtract.
     * @return a {@code BigFraction} instance with the resulting values.
     */
    public BigFraction subtract(final int i) {
        return subtract(BigInteger.valueOf(i));
    }

    /**
     * <p>
     * Subtracts the value of a {@code long} from the value of this
     * {@code BigFraction}, returning the result in reduced form.
     * </p>
     *
     * @param l the {@code long} to subtract.
     * @return a {@code BigFraction} instance with the resulting values.
     */
    public BigFraction subtract(final long l) {
        return subtract(BigInteger.valueOf(l));
    }

    /**
     * <p>
     * Subtracts the value of another fraction from the value of this one,
     * returning the result in reduced form.
     * </p>
     *
     * @param fraction {@link BigFraction} to subtract, must not be {@code null}.
     * @return a {@link BigFraction} instance with the resulting values
     */
    public BigFraction subtract(final BigFraction fraction) {
        checkNotNull(fraction, PARAM_NAME_FRACTION);
        if (fraction.numerator.signum() == 0) {
            return this;
        }
        if (numerator.signum() == 0) {
            return fraction.negate();
        }

        final BigInteger num;
        final BigInteger den;
        if (denominator.equals(fraction.denominator)) {
            num = numerator.subtract(fraction.numerator);
            den = denominator;
        } else {
            num = (numerator.multiply(fraction.denominator)).subtract((fraction.numerator).multiply(denominator));
            den = denominator.multiply(fraction.denominator);
        }
        return new BigFraction(num, den);

    }

    /**
     * <p>
     * Returns the <code>String</code> representing this fraction, ie
     * "num / dem" or just "num" if the denominator is one.
     * </p>
     *
     * @return a string representation of the fraction.
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final String str;
        if (BigInteger.ONE.equals(denominator)) {
            str = numerator.toString();
        } else if (BigInteger.ZERO.equals(numerator)) {
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
    public static BigFraction parse(String s) {
        s = s.replace(",", "");
        final int slashLoc = s.indexOf("/");
        // if no slash, parse as single number
        if (slashLoc == -1) {
            return BigFraction.of(new BigInteger(s.trim()));
        } else {
            final BigInteger num = new BigInteger(
                    s.substring(0, slashLoc).trim());
            final BigInteger denom = new BigInteger(s.substring(slashLoc + 1).trim());
            return of(num, denom);
        }
    }


    /**
     * Check that the argument is not null and throw a NullPointerException
     * if it is.
     * @param arg     the argument to check
     * @param argName the name of the argument
     */
    private static void checkNotNull(Object arg, String argName) {
        if (arg == null) {
            throw new NullPointerException(argName);
        }
    }

}
