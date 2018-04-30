/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.csa.rstb.polarimetric.gpf;

import Jama.Matrix;
import org.esa.s1tbx.commons.polsar.PolBandUtils;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.engine_utilities.eo.Constants;
import org.esa.snap.engine_utilities.gpf.TileIndex;

import java.awt.*;


/**
 * Common dual pol code used by polarimetric operators
 */
public final class DualPolOpUtils {

    public static final double EPS = Constants.EPS;


    public static void getDataBuffer(final Operator op, final Band[] srcBands, final Rectangle sourceRectangle,
                                     final PolBandUtils.MATRIX sourceProductType,
                                     final Tile[] sourceTiles, final ProductData[] dataBuffers) {

        for (Band band : srcBands) {
            final String bandName = band.getName();
            if (sourceProductType == PolBandUtils.MATRIX.C2) {

                if (bandName.contains("C11")) {
                    sourceTiles[0] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[0] = sourceTiles[0].getDataBuffer();
                } else if (bandName.contains("C12_real")) {
                    sourceTiles[1] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[1] = sourceTiles[1].getDataBuffer();
                } else if (bandName.contains("C12_imag")) {
                    sourceTiles[2] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[2] = sourceTiles[2].getDataBuffer();
                } else if (bandName.contains("C22")) {
                    sourceTiles[3] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[3] = sourceTiles[3].getDataBuffer();
                }

            } else if (sourceProductType == PolBandUtils.MATRIX.DUAL_HH_HV) {

                if (bandName.contains("i_HH")) {
                    sourceTiles[0] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[0] = sourceTiles[0].getDataBuffer();
                } else if (bandName.contains("q_HH")) {
                    sourceTiles[1] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[1] = sourceTiles[1].getDataBuffer();
                } else if (bandName.contains("i_HV")) {
                    sourceTiles[2] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[2] = sourceTiles[2].getDataBuffer();
                } else if (bandName.contains("q_HV")) {
                    sourceTiles[3] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[3] = sourceTiles[3].getDataBuffer();
                }

            } else if (sourceProductType == PolBandUtils.MATRIX.DUAL_HH_VV) {

                if (bandName.contains("i_HH")) {
                    sourceTiles[0] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[0] = sourceTiles[0].getDataBuffer();
                } else if (bandName.contains("q_HH")) {
                    sourceTiles[1] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[1] = sourceTiles[1].getDataBuffer();
                } else if (bandName.contains("i_VV")) {
                    sourceTiles[2] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[2] = sourceTiles[2].getDataBuffer();
                } else if (bandName.contains("q_VV")) {
                    sourceTiles[3] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[3] = sourceTiles[3].getDataBuffer();
                }

            } else if (sourceProductType == PolBandUtils.MATRIX.DUAL_VH_VV) {

                if (bandName.contains("i_VH")) {
                    sourceTiles[0] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[0] = sourceTiles[0].getDataBuffer();
                } else if (bandName.contains("q_VH")) {
                    sourceTiles[1] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[1] = sourceTiles[1].getDataBuffer();
                } else if (bandName.contains("i_VV")) {
                    sourceTiles[2] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[2] = sourceTiles[2].getDataBuffer();
                } else if (bandName.contains("q_VV")) {
                    sourceTiles[3] = op.getSourceTile(band, sourceRectangle);
                    dataBuffers[3] = sourceTiles[3].getDataBuffer();
                }
            }
        }
    }

    /**
     * Get mean covariance matrix C2 for given pixel.
     *
     * @param x                 X coordinate of the given pixel.
     * @param y                 Y coordinate of the given pixel.
     * @param halfWindowSizeX   The sliding window width /2
     * @param halfWindowSizeY   The sliding window height /2
     * @param sourceImageWidth  Source image width.
     * @param sourceImageHeight Source image height.
     * @param sourceProductType The source product type.
     * @param sourceTiles       The source tiles for all bands.
     * @param dataBuffers       Source tile data buffers.
     * @param Cr                The real part of the mean covariance matrix.
     * @param Ci                The imaginary part of the mean covariance matrix.
     */
    public static void getMeanCovarianceMatrixC2(
            final int x, final int y, final int halfWindowSizeX, final int halfWindowSizeY, final int sourceImageWidth,
            final int sourceImageHeight, final PolBandUtils.MATRIX sourceProductType,
            final Tile[] sourceTiles, final ProductData[] dataBuffers, final double[][] Cr, final double[][] Ci) {

        final int xSt = Math.max(x - halfWindowSizeX, 0);
        final int xEd = Math.min(x + halfWindowSizeX, sourceImageWidth - 1);
        final int ySt = Math.max(y - halfWindowSizeY, 0);
        final int yEd = Math.min(y + halfWindowSizeY, sourceImageHeight - 1);
        final int num = (yEd - ySt + 1) * (xEd - xSt + 1);

        final TileIndex srcIndex = new TileIndex(sourceTiles[0]);

        final Matrix CrMat = new Matrix(2, 2);
        final Matrix CiMat = new Matrix(2, 2);

        if (sourceProductType == PolBandUtils.MATRIX.C2) {

            for (int yy = ySt; yy <= yEd; ++yy) {
                srcIndex.calculateStride(yy);
                for (int xx = xSt; xx <= xEd; ++xx) {
                    final Matrix tmpCrMat = new Matrix(2, 2);
                    final Matrix tmpCiMat = new Matrix(2, 2);
                    getCovarianceMatrixC2(srcIndex.getIndex(xx), dataBuffers, tmpCrMat.getArray(), tmpCiMat.getArray());
                    CrMat.plusEquals(tmpCrMat);
                    CiMat.plusEquals(tmpCiMat);
                }
            }

        } else if (sourceProductType == PolBandUtils.MATRIX.LCHCP ||
                   sourceProductType == PolBandUtils.MATRIX.RCHCP ||
                   sourceProductType == PolBandUtils.MATRIX.DUAL_HH_HV ||
                   sourceProductType == PolBandUtils.MATRIX.DUAL_VH_VV ||
                   sourceProductType == PolBandUtils.MATRIX.DUAL_HH_VV) {

            final double[] tempKr = new double[2];
            final double[] tempKi = new double[2];

            for (int yy = ySt; yy <= yEd; ++yy) {
                srcIndex.calculateStride(yy);
                for (int xx = xSt; xx <= xEd; ++xx) {
                    final Matrix tmpCrMat = new Matrix(2, 2);
                    final Matrix tmpCiMat = new Matrix(2, 2);
                    getScatterVector(srcIndex.getIndex(xx), dataBuffers, tempKr, tempKi);
                    computeCovarianceMatrixC2(tempKr, tempKi, tmpCrMat.getArray(), tmpCiMat.getArray());
                    CrMat.plusEquals(tmpCrMat);
                    CiMat.plusEquals(tmpCiMat);
                }
            }

        } else {
            throw new OperatorException("getMeanCovarianceMatrixC2 not implemented for raw dual pol");
        }

        CrMat.timesEquals(1.0 / num);
        CiMat.timesEquals(1.0 / num);

        Cr[0][0] = CrMat.get(0, 0);
        Ci[0][0] = CiMat.get(0, 0);
        Cr[0][1] = CrMat.get(0, 1);
        Ci[0][1] = CiMat.get(0, 1);

        Cr[1][0] = CrMat.get(1, 0);
        Ci[1][0] = CiMat.get(1, 0);
        Cr[1][1] = CrMat.get(1, 1);
        Ci[1][1] = CiMat.get(1, 1);
    }

    /**
     * Get covariance matrix C2 for a given pixel in the input C2 product.
     *
     * @param index       X,Y coordinate of the given pixel
     * @param dataBuffers Source tile data buffers for all 4 source bands
     * @param Cr          Real part of the 2x2 covariance matrix
     * @param Ci          Imaginary part of the 2x2 covariance matrix
     */
    public static void getCovarianceMatrixC2(final int index, final ProductData[] dataBuffers,
                                             final double[][] Cr, final double[][] Ci) {

        Cr[0][0] = dataBuffers[0].getElemDoubleAt(index); // C11 - real
        Ci[0][0] = 0.0;                                   // C11 - imag

        Cr[0][1] = dataBuffers[1].getElemDoubleAt(index); // C12 - real
        Ci[0][1] = dataBuffers[2].getElemDoubleAt(index); // C12 - imag

        Cr[1][1] = dataBuffers[3].getElemDoubleAt(index); // C22 - real
        Ci[1][1] = 0.0;                                   // C22 - imag

        Cr[1][0] = Cr[0][1];
        Ci[1][0] = -Ci[0][1];
    }

    /**
     * Get covariance matrix C2 for a given pixel.
     *
     * @param index       X,Y coordinate of the given pixel
     * @param dataBuffers Source tile data buffers for all 4 source bands
     * @param Cr          Real part of the 2x2 covariance matrix
     * @param Ci          Imaginary part of the 2x2 covariance matrix
     */
    public static void getCovarianceMatrixC2(final int index, final PolBandUtils.MATRIX sourceProductType,
                                             final ProductData[] dataBuffers, final double[][] Cr,
                                             final double[][] Ci) {

        if (sourceProductType == PolBandUtils.MATRIX.LCHCP ||
                sourceProductType == PolBandUtils.MATRIX.RCHCP ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_HH_HV ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_VH_VV ||
                sourceProductType == PolBandUtils.MATRIX.DUAL_HH_VV) {

            final double[] kr = new double[2];
            final double[] ki = new double[2];
            getScatterVector(index, dataBuffers, kr, ki);
            computeCovarianceMatrixC2(kr, ki, Cr, Ci);

        } else if (sourceProductType == PolBandUtils.MATRIX.C2) {
            getCovarianceMatrixC2(index, dataBuffers, Cr, Ci);
        }
    }

    /**
     * Get compact-pol or dual-pol scatter vector for a given pixel in the input product.
     *
     * @param index       X,Y coordinate of the given pixel
     * @param dataBuffers Source tiles dataBuffers for all 4 source bands
     * @param kr          Real part of the scatter vector
     * @param ki          Imaginary part of the scatter vector
     */
    public static void getScatterVector(final int index, final ProductData[] dataBuffers,
                                        final double[] kr, final double[] ki) {

        kr[0] = dataBuffers[0].getElemDoubleAt(index);
        ki[0] = dataBuffers[1].getElemDoubleAt(index);

        kr[1] = dataBuffers[2].getElemDoubleAt(index);
        ki[1] = dataBuffers[3].getElemDoubleAt(index);
    }

    /**
     * Compute covariance matrix c2 for given dual pol or complex compact pol 2x1 scatter vector.
     *
     * For dual pol product:
     *
     * Case 1) k_DP1 = [S_HH
     *                  S_HV]
     *         kr[0] = i_hh, ki[0] = q_hh, kr[1] = i_hv, ki[1] = q_hv
     *
     * Case 2) k_DP2 = [S_VH
     *                  S_VV]
     *         kr[0] = i_vh, ki[0] = q_vh, kr[1] = i_vv, ki[1] = q_vv
     *
     * Case 3) k_DP3 = [S_HH
     *                  S_VV]
     *         kr[0] = i_hh, ki[0] = q_hh, kr[1] = i_vv, ki[1] = q_vv
     *
     * @param kr Real part of the scatter vector
     * @param ki Imaginary part of the scatter vector
     * @param Cr Real part of the covariance matrix
     * @param Ci Imaginary part of the covariance matrix
     */
    public static void computeCovarianceMatrixC2(final double[] kr, final double[] ki,
                                                 final double[][] Cr, final double[][] Ci) {

        Cr[0][0] = kr[0] * kr[0] + ki[0] * ki[0];
        Ci[0][0] = 0.0;

        Cr[0][1] = kr[0] * kr[1] + ki[0] * ki[1];
        Ci[0][1] = ki[0] * kr[1] - kr[0] * ki[1];

        Cr[1][1] = kr[1] * kr[1] + ki[1] * ki[1];
        Ci[1][1] = 0.0;

        Cr[1][0] = Cr[0][1];
        Ci[1][0] = -Ci[0][1];
    }

    public static void getMeanCorrelationMatrixC2(
            final int x, final int y, final int halfWindowSizeX, final int halfWindowSizeY,
            final int sourceImageWidth, final int sourceImageHeight, final PolBandUtils.MATRIX sourceProductType,
            final Tile[] sourceTiles, final ProductData[] mstDataBuffers, final ProductData[] slvDataBuffers,
            final double[][] Cr, final double[][] Ci) {

        final int xSt = Math.max(x - halfWindowSizeX, 0);
        final int xEd = Math.min(x + halfWindowSizeX, sourceImageWidth - 1);
        final int ySt = Math.max(y - halfWindowSizeY, 0);
        final int yEd = Math.min(y + halfWindowSizeY, sourceImageHeight - 1);
        final int num = (yEd - ySt + 1) * (xEd - xSt + 1);

        final TileIndex srcIndex = new TileIndex(sourceTiles[0]);

        final Matrix CrMat = new Matrix(2, 2);
        final Matrix CiMat = new Matrix(2, 2);

        if (sourceProductType == PolBandUtils.MATRIX.LCHCP ||
            sourceProductType == PolBandUtils.MATRIX.RCHCP ||
            sourceProductType == PolBandUtils.MATRIX.DUAL_HH_HV ||
            sourceProductType == PolBandUtils.MATRIX.DUAL_VH_VV ||
            sourceProductType == PolBandUtils.MATRIX.DUAL_HH_VV) {

            final double[] K1r = new double[2];
            final double[] K1i = new double[2];
            final double[] K2r = new double[2];
            final double[] K2i = new double[2];

            for (int yy = ySt; yy <= yEd; ++yy) {
                srcIndex.calculateStride(yy);
                for (int xx = xSt; xx <= xEd; ++xx) {
                    final int idx = srcIndex.getIndex(xx);

                    final Matrix tmpCrMat = new Matrix(2, 2);
                    final Matrix tmpCiMat = new Matrix(2, 2);

                    getScatterVector(idx, mstDataBuffers, K1r, K1i);
                    getScatterVector(idx, slvDataBuffers, K2r, K2i);

                    computeCorrelationMatrixC2(K1r, K1i, K2r, K2i, tmpCrMat.getArray(), tmpCiMat.getArray());

                    CrMat.plusEquals(tmpCrMat);
                    CiMat.plusEquals(tmpCiMat);
                }
            }

        } else {
            throw new OperatorException("getMeanCorrelationMatrix: input should be raw dual pol data");
        }

        CrMat.timesEquals(1.0 / num);
        CiMat.timesEquals(1.0 / num);

        Cr[0][0] = CrMat.get(0, 0);
        Ci[0][0] = CiMat.get(0, 0);
        Cr[0][1] = CrMat.get(0, 1);
        Ci[0][1] = CiMat.get(0, 1);

        Cr[1][0] = CrMat.get(1, 0);
        Ci[1][0] = CiMat.get(1, 0);
        Cr[1][1] = CrMat.get(1, 1);
        Ci[1][1] = CiMat.get(1, 1);
    }

    public static void computeCorrelationMatrixC2(final double[] k1r, final double[] k1i,
                                                  final double[] k2r, final double[] k2i,
                                                  final double[][] Cr, final double[][] Ci) {

        Cr[0][0] = k1r[0] * k2r[0] + k1i[0] * k2i[0];
        Ci[0][0] = k1i[0] * k2r[0] - k1r[0] * k2i[0];

        Cr[0][1] = k1r[0] * k2r[1] + k1i[0] * k2i[1];
        Ci[0][1] = k1i[0] * k2r[1] - k1r[0] * k2i[1];

        Cr[1][0] = k1r[1] * k2r[0] + k1i[1] * k2i[0];
        Ci[1][0] = k1i[1] * k2r[0] - k1r[1] * k2i[0];

        Cr[1][1] = k1r[1] * k2r[1] + k1i[1] * k2i[1];
        Ci[1][1] = k1i[1] * k2r[1] - k1r[1] * k2i[1];
    }
}
