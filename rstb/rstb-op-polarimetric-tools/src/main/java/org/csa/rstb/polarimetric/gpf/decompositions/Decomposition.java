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
package org.csa.rstb.polarimetric.gpf.decompositions;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.Tile;

import java.awt.*;
import java.util.Map;

/**
 * Interface for polarimetric decompositions
 */
public interface Decomposition {

    /**
     * Return the list of band names for the target product
     *
     * @return list of band names
     */
    String[] getTargetBandNames();

    String getSuffix();

    /**
     * Sets the unit for the new target band
     *
     * @param targetBandName the band name
     * @param targetBand     the new target band
     */
    void setBandUnit(final String targetBandName, final Band targetBand);

    /**
     * Perform decomposition for given tile.
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed.
     * @param op              the polarimetric decomposition operator
     * @throws OperatorException If an error occurs during computation of the filtered value.
     */
    void computeTile(final Map<Band, Tile> targetTiles, final Rectangle targetRectangle,
                            final Operator op);

    default boolean isNoData(final double[][] matrix_i, final double[][] matrix_q, final double nodatavalue) {
        return matrix_i[0][0] == nodatavalue && matrix_q[0][0] == nodatavalue;
    }

    default boolean isNoData(final ProductData[] dataBuffers, final int index, final double nodatavalue) {
        return dataBuffers[0].getElemDoubleAt(index) == nodatavalue && dataBuffers[1].getElemDoubleAt(index) == nodatavalue;
    }
}
