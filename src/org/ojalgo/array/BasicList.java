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
package org.ojalgo.array;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;

import org.ojalgo.access.Access1D;
import org.ojalgo.access.Iterator1D;
import org.ojalgo.access.Mutate1D;
import org.ojalgo.scalar.ComplexNumber;
import org.ojalgo.scalar.Quaternion;
import org.ojalgo.scalar.RationalNumber;

public final class BasicList<N extends Number> implements List<N>, RandomAccess, Access1D<N>, Mutate1D {

    private static long INITIAL_CAPACITY = 16L;
    private static long SEGMENT_CAPACITY = 16_384L;

    public static BasicList<BigDecimal> makeBig() {
        return new BasicList<BigDecimal>(BigArray.FACTORY);
    }

    public static BasicList<ComplexNumber> makeComplexe() {
        return new BasicList<ComplexNumber>(ComplexArray.FACTORY);
    }

    public static BasicList<Double> makeOffHeap() {
        return new BasicList<Double>(OffHeapArray.FACTORY);
    }

    public static BasicList<Double> makePrimitive() {
        return new BasicList<Double>(PrimitiveArray.FACTORY);
    }

    public static BasicList<Quaternion> makeQuaternion() {
        return new BasicList<Quaternion>(QuaternionArray.FACTORY);
    }

    public static BasicList<RationalNumber> makeRational() {
        return new BasicList<RationalNumber>(RationalArray.FACTORY);
    }

    private long myActualCount;
    private final ArrayFactory<N> myArrayFactory;
    private BasicArray<N> myStorage;

    public BasicList(final ArrayFactory<N> arrayFactory) {

        super();

        myArrayFactory = arrayFactory;

        myStorage = arrayFactory.makeZero(INITIAL_CAPACITY);
        myActualCount = 0L;
    }

    public boolean add(final double e) {

        this.ensureCapacity();

        myStorage.set(myActualCount++, e);

        return true;
    }

    public void add(final int index, final N element) {
        throw new UnsupportedOperationException();
    }

    public void add(final long index, final double addend) {
        if (index >= myActualCount) {
            throw new ArrayIndexOutOfBoundsException();
        } else {
            myStorage.add(index, addend);
        }
    }

    public void add(final long index, final Number addend) {
        if (index >= myActualCount) {
            throw new ArrayIndexOutOfBoundsException();
        } else {
            myStorage.add(index, addend);
        }
    }

    public boolean add(final N e) {

        this.ensureCapacity();

        myStorage.set(myActualCount++, e);

        return true;
    }

    public boolean addAll(final Collection<? extends N> elements) {
        for (final N tmpElement : elements) {
            this.add(tmpElement);
        }
        return true;
    }

    public boolean addAll(final int index, final Collection<? extends N> c) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        myActualCount = 0L;
    }

    public boolean contains(final Object object) {
        if (object instanceof Number) {
            return this.indexOf(object) >= 0;
        } else {
            return false;
        }
    }

    public boolean containsAll(final Collection<?> c) {
        for (final Object tmpObject : c) {
            if (!this.contains(tmpObject)) {
                return false;
            }
        }
        return true;
    }

    public long count() {
        return myActualCount;
    }

    public double doubleValue(final long index) {
        if (index >= myActualCount) {
            throw new ArrayIndexOutOfBoundsException();
        } else {
            return myStorage.doubleValue(index);
        }
    }

    public N get(final int index) {
        if (index >= myActualCount) {
            throw new ArrayIndexOutOfBoundsException();
        } else {
            return myStorage.get(index);
        }
    }

    public N get(final long index) {
        if (index >= myActualCount) {
            throw new ArrayIndexOutOfBoundsException();
        } else {
            return myStorage.get(index);
        }
    }

    public int indexOf(final Object object) {
        final ListIterator<N> tmpIterator = this.listIterator();
        if (object == null) {
            while (tmpIterator.hasNext()) {
                if (tmpIterator.next() == null) {
                    return tmpIterator.previousIndex();
                }
            }
        } else {
            while (tmpIterator.hasNext()) {
                if (object.equals(tmpIterator.next())) {
                    return tmpIterator.previousIndex();
                }
            }
        }
        return -1;
    }

    public boolean isEmpty() {
        return myActualCount == 0L;
    }

    public Iterator<N> iterator() {
        return Access1D.super.iterator();
    }

    public int lastIndexOf(final Object object) {
        final ListIterator<N> tmpIterator = this.listIterator(this.size());
        if (object == null) {
            while (tmpIterator.hasPrevious()) {
                if (tmpIterator.previous() == null) {
                    return tmpIterator.nextIndex();
                }
            }
        } else {
            while (tmpIterator.hasPrevious()) {
                if (object.equals(tmpIterator.previous())) {
                    return tmpIterator.nextIndex();
                }
            }
        }
        return -1;
    }

    public ListIterator<N> listIterator() {
        return new Iterator1D<N>(this);
    }

    public ListIterator<N> listIterator(final int index) {
        return new Iterator1D<N>(this, index);
    }

    public N remove(final int index) {
        throw new UnsupportedOperationException();
    }

    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public N set(final int index, final N element) {
        throw new UnsupportedOperationException();
    }

    public void set(final long index, final double value) {
        if (index >= myActualCount) {
            throw new ArrayIndexOutOfBoundsException();
        } else {
            myStorage.set(index, value);
        }
    }

    public void set(final long index, final Number value) {
        if (index >= myActualCount) {
            throw new ArrayIndexOutOfBoundsException();
        } else {
            myStorage.set(index, value);
        }
    }

    public int size() {
        return (int) myActualCount;
    }

    public List<N> subList(final int fromIndex, final int toIndex) {
        final BasicList<N> retVal = new BasicList<>(myArrayFactory);
        if (myStorage instanceof PrimitiveArray) {
            for (int i = 0; i < toIndex; i++) {
                retVal.add(this.doubleValue(i));
            }
        } else {
            for (int i = 0; i < toIndex; i++) {
                retVal.add(this.get(i));
            }
        }
        return retVal;
    }

    public Object[] toArray() {
        return this.toArray(new Object[this.size()]);
    }

    @SuppressWarnings("unchecked")
    public <T> T[] toArray(final T[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = (T) myStorage.get(i);
        }
        return array;
    }

    private void ensureCapacity() {

        if (myStorage.count() > myActualCount) {
            // It fits, just add to the end

        } else if ((myStorage.count() % SEGMENT_CAPACITY) == 0L) {
            // Doesn't fit, grow by 1 segment, then add

            if (myStorage instanceof SegmentedArray) {
                myStorage = ((SegmentedArray<N>) myStorage).grow();
            } else if (myStorage.count() == SEGMENT_CAPACITY) {
                myStorage = myArrayFactory.wrapAsSegments(myStorage, myArrayFactory.makeZero(SEGMENT_CAPACITY));
            } else {
                throw new IllegalStateException();
            }

        } else {
            // Doesn't fit, grow by doubling the capacity, then add

            final BasicArray<N> tmpStorage = myArrayFactory.makeZero(myStorage.count() * 2L);
            tmpStorage.fillMatching(myStorage);
            myStorage = tmpStorage;
        }
    }

}