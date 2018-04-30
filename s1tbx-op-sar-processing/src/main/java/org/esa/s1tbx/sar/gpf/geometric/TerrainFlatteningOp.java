/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.s1tbx.sar.gpf.geometric;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.math3.util.FastMath;
import org.esa.s1tbx.insar.gpf.support.SARGeocoding;
import org.esa.s1tbx.insar.gpf.support.SARUtils;
import org.esa.s1tbx.commons.polsar.PolBandUtils;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.core.dataop.dem.ElevationModel;
import org.esa.snap.core.dataop.resamp.ResamplingFactory;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.dem.dataio.DEMFactory;
import org.esa.snap.dem.dataio.FileElevationModel;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.datamodel.OrbitStateVector;
import org.esa.snap.engine_utilities.datamodel.PosVector;
import org.esa.snap.engine_utilities.datamodel.Unit;
import org.esa.snap.engine_utilities.eo.Constants;
import org.esa.snap.engine_utilities.eo.GeoUtils;
import org.esa.snap.engine_utilities.gpf.InputProductValidator;
import org.esa.snap.engine_utilities.gpf.OperatorUtils;
import org.esa.snap.engine_utilities.gpf.ReaderUtils;
import org.esa.snap.engine_utilities.gpf.TileGeoreferencing;
import org.esa.snap.engine_utilities.gpf.TileIndex;
import org.esa.snap.engine_utilities.util.Maths;

import java.awt.Rectangle;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * This operator implements the terrain flattening algorithm proposed by
 * David Small. For details, see the paper below and the references therein.
 * David Small, "Flattening Gamma: Radiometric Terrain Correction for SAR imagery",
 * IEEE Transaction on Geoscience and Remote Sensing, Vol. 48, No. 8, August 2011.
 */

@OperatorMetadata(alias = "Terrain-Flattening",
        category = "Radar/Radiometric",
        authors = "Jun Lu, Luis Veci",
        version = "1.0",
        copyright = "Copyright (C) 2014 by Array Systems Computing Inc.",
        description = "Terrain Flattening")
public final class TerrainFlatteningOp extends Operator {

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct;

    @Parameter(description = "The list of source bands.", alias = "sourceBands",
            rasterDataNodeType = Band.class, label = "Source Bands")
    private String[] sourceBandNames;

    @Parameter(description = "The digital elevation model.",
            defaultValue = "SRTM 1Sec HGT", label = "Digital Elevation Model")
    private String demName = "SRTM 1Sec HGT";

    @Parameter(defaultValue = ResamplingFactory.BICUBIC_INTERPOLATION_NAME,
            label = "DEM Resampling Method")
    private String demResamplingMethod = ResamplingFactory.BICUBIC_INTERPOLATION_NAME;

    @Parameter(label = "External DEM")
    private File externalDEMFile = null;

    @Parameter(label = "DEM No Data Value", defaultValue = "0")
    private double externalDEMNoDataValue = 0;

    @Parameter(defaultValue = "false", label = "Output Simulated Image")
    private Boolean outputSimulatedImage = false;

    @Parameter(defaultValue = "true", label = "Re-grid method")
    private Boolean reGridMethod = true;

    private ElevationModel dem = null;
    private FileElevationModel fileElevationModel = null;
    private TiePointGrid incidenceAngleTPG = null;
    private GeoCoding targetGeoCoding = null;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private boolean srgrFlag = false;
    private boolean isElevationModelAvailable = false;
    private boolean isGRD = false;
    private boolean isPolSar = false;

    private double rangeSpacing = 0.0;
    private double azimuthSpacing = 0.0;
    private double firstLineUTC = 0.0; // in days
    private double lastLineUTC = 0.0; // in days
    private double lineTimeInterval = 0.0; // in days
    private double nearEdgeSlantRange = 0.0; // in m
    private double wavelength = 0.0; // in m
    private double demNoDataValue = 0; // no data value for DEM
    private double overSamplingFactor = 1.0;
    private SARGeocoding.Orbit orbit = null;

    private Double noDataValue = 0.0;
    private double beta0 = 0;

    private OrbitStateVector[] orbitStateVectors = null;
    private AbstractMetadata.SRGRCoefficientList[] srgrConvParams = null;
    private Band[] targetBands = null;
    private final HashMap<Band, Band> targetBandToSourceBandMap = new HashMap<>(2);
    private boolean nearRangeOnLeft = true;
    private boolean orbitOnWest = true;

    // set this flag to true to output terrain flattened sigma0
    private boolean outputSigma0 = false;
    private boolean detectShadow = true;
    private double threshold = 0.05;
    private boolean invalidSource = false;

    private static final String PRODUCT_SUFFIX = "_TF";

    enum UnitType {AMPLITUDE, INTENSITY, COMPLEX, RATIO}

    private static final String[] BAND_PREFIX = new String[] { "Beta0",
            "T11", "T12", "T13", "T22", "T23", "T33",
            "C11", "C12", "C13", "C22", "C23", "C33",
            "C11", "C12", "C13", "T22"};

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link Product}
     * annotated with the {@link TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws OperatorException If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        try {
            final InputProductValidator validator = new InputProductValidator(sourceProduct);
            validator.checkIfSARProduct();
            validator.checkIfMapProjected(false);

            PolBandUtils.MATRIX sourceProductType = PolBandUtils.getSourceProductType(sourceProduct);
            if (sourceProductType.equals(PolBandUtils.MATRIX.T3) || sourceProductType.equals(PolBandUtils.MATRIX.C3)
                    || sourceProductType.equals(PolBandUtils.MATRIX.C2)) {
                isPolSar = true;
            } else if (!validator.isCalibrated()) {
                throw new OperatorException("Source product must be calibrated to beta0 or be in T3, C3, C2 matrix format");
            }

            getMetadata();

            getTiePointGrid();

            getSourceImageDimension();

            computeSensorPositionsAndVelocities();

            createTargetProduct();

            if (externalDEMFile == null) {
                DEMFactory.checkIfDEMInstalled(demName);
            }

            DEMFactory.validateDEM(demName, sourceProduct);

            noDataValue = sourceProduct.getBands()[0].getNoDataValue();

            beta0 = azimuthSpacing * rangeSpacing;

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    @Override
    public synchronized void dispose() {
        if (dem != null) {
            dem.dispose();
            dem = null;
        }
        if (fileElevationModel != null) {
            fileElevationModel.dispose();
        }
    }

    /**
     * Retrieve required data from Abstracted Metadata
     *
     * @throws Exception if metadata not found
     */
    private void getMetadata() throws Exception {

        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);
        rangeSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.range_spacing);
        azimuthSpacing = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.azimuth_spacing);
        final double minSpacing = Math.min(rangeSpacing, azimuthSpacing);

        if (reGridMethod != null && reGridMethod) {
            if (demName.contains("SRTM 3Sec") && (rangeSpacing < 90.0 || azimuthSpacing < 90.0)) {
                overSamplingFactor = Math.round(90.0 / minSpacing);
            } else if (demName.contains("SRTM 1Sec HGT") && (rangeSpacing < 30.0 || azimuthSpacing < 30.0)) {
                overSamplingFactor = Math.round(30.0 / minSpacing);
            } else if (demName.contains("SRTM 1Sec Grid") && (rangeSpacing < 30.0 || azimuthSpacing < 30.0)) {
                overSamplingFactor = Math.round(30.0 / minSpacing);
            } else if (demName.contains("ASTER 1sec GDEM") && (rangeSpacing < 30.0 || azimuthSpacing < 30.0)) {
                overSamplingFactor = Math.round(30.0 / minSpacing);
            } else if (demName.contains("ACE30") && (rangeSpacing < 1000.0 || azimuthSpacing < 1000.0)) {
                overSamplingFactor = Math.round(1000.0 / minSpacing);
            } else if (demName.contains("ACE2_5Min") && (rangeSpacing < 10000.0 || azimuthSpacing < 10000.0)) {
                overSamplingFactor = Math.round(1000.0 / minSpacing);
            } else if (demName.contains("GETASSE30") && (rangeSpacing < 1000.0 || azimuthSpacing < 1000.0)) {
                overSamplingFactor = Math.round(1000.0 / minSpacing);
            }
        }

        srgrFlag = AbstractMetadata.getAttributeBoolean(absRoot, AbstractMetadata.srgr_flag);
        wavelength = SARUtils.getRadarFrequency(absRoot);
        firstLineUTC = AbstractMetadata.parseUTC(absRoot.getAttributeString(AbstractMetadata.first_line_time)).getMJD(); // in days
        lastLineUTC = AbstractMetadata.parseUTC(absRoot.getAttributeString(AbstractMetadata.last_line_time)).getMJD(); // in days
        lineTimeInterval = absRoot.getAttributeDouble(AbstractMetadata.line_time_interval) / Constants.secondsInDay; // s to day
        orbitStateVectors = AbstractMetadata.getOrbitStateVectors(absRoot);

        if (srgrFlag) {
            srgrConvParams = AbstractMetadata.getSRGRCoefficients(absRoot);
        } else {
            nearEdgeSlantRange = AbstractMetadata.getAttributeDouble(absRoot, AbstractMetadata.slant_range_to_first_pixel);
        }

        final String mission = RangeDopplerGeocodingOp.getMissionType(absRoot);
        final String pass = absRoot.getAttributeString(AbstractMetadata.PASS);
        if (mission.equals("RS2") && pass.contains("DESCENDING")) {
            nearRangeOnLeft = false;
        }

        String antennaPointing = absRoot.getAttributeString(AbstractMetadata.antenna_pointing);
        if (!antennaPointing.contains("right") && !antennaPointing.contains("left")) {
            antennaPointing = "right";
        }

        if ((pass.contains("DESCENDING") && antennaPointing.contains("right")) ||
                (pass.contains("ASCENDING") && antennaPointing.contains("left"))) {
            orbitOnWest = false;
        }

//        if (mission.contains("CSKS") || mission.contains("TSX") || mission.equals("RS2") || mission.contains("SENTINEL")) {
//            skipBistaticCorrection = true;
//        }

        final String sampleType = absRoot.getAttributeString(AbstractMetadata.SAMPLE_TYPE);
        if (!sampleType.contains("COMPLEX")) {
            isGRD = true;
        }
    }

    /**
     * Get source image width and height.
     */
    private void getSourceImageDimension() {
        sourceImageWidth = sourceProduct.getSceneRasterWidth();
        sourceImageHeight = sourceProduct.getSceneRasterHeight();
    }

    /**
     * Compute sensor position and velocity for each range line.
     */
    private void computeSensorPositionsAndVelocities() {

        orbit = new SARGeocoding.Orbit(orbitStateVectors, firstLineUTC, lineTimeInterval, sourceImageHeight);
    }

    /**
     * Get tie point grids.
     */
    private void getTiePointGrid() {
        incidenceAngleTPG = OperatorUtils.getIncidenceAngle(sourceProduct);
        if (incidenceAngleTPG == null) {
            throw new OperatorException("Product without incidence angle tie point grid");
        }
    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName() + PRODUCT_SUFFIX,
                sourceProduct.getProductType(),
                sourceImageWidth,
                sourceImageHeight);

        addSelectedBands();

        ProductUtils.copyProductNodes(sourceProduct, targetProduct);

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);

        if (externalDEMFile != null && fileElevationModel == null) { // if external DEM file is specified by user
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, externalDEMFile.getPath());
        } else {
            AbstractMetadata.setAttribute(absTgt, AbstractMetadata.DEM, demName);
        }

        absTgt.setAttributeString("DEM resampling method", demResamplingMethod);
        absTgt.setAttributeInt(AbstractMetadata.abs_calibration_flag, 1);

        if (externalDEMFile != null) {
            absTgt.setAttributeDouble("external DEM no data value", externalDEMNoDataValue);
        }

        targetGeoCoding = targetProduct.getSceneGeoCoding();
    }

    /**
     * Add user selected bands to target product.
     */
    private void addSelectedBands() {

        final Band[] sourceBands = OperatorUtils.getSourceBands(sourceProduct, sourceBandNames, true);
        final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

        String gamma0BandName, sigma0BandName = null;
        String tgtUnit;
        for (final Band srcBand : sourceBands) {
            final String srcBandName = srcBand.getName();

            //beta0 or polsar product
            boolean valid = false;
            for(String validPrefix : BAND_PREFIX) {
                if(srcBandName.startsWith(validPrefix)) {
                    valid = true;
                    break;
                }
            }
            if (!valid) {
                continue;
            }

            if (isPolSar) {
                if (targetProduct.getBand(srcBandName) == null) {
                    Band tgtBand = targetProduct.addBand(srcBandName, ProductData.TYPE_FLOAT32);
                    tgtBand.setUnit(srcBand.getUnit());
                    tgtBand.setNoDataValue(srcBand.getNoDataValue());
                    tgtBand.setNoDataValueUsed(srcBand.isNoDataValueUsed());
                    tgtBand.setDescription(srcBand.getDescription());
                    targetBandToSourceBandMap.put(tgtBand, srcBand);
                }
            } else {

                final String unit = srcBand.getUnit();
                if (unit == null) {
                    throw new OperatorException("band " + srcBandName + " requires a unit");
                }

                if (unit.contains(Unit.DB)) {
                    throw new OperatorException("Terrain flattening of bands in dB is not supported");
                } else if (unit.contains(Unit.PHASE)) {
                    continue;
                } else if (unit.contains(Unit.REAL) || unit.contains(Unit.IMAGINARY)) {
                    gamma0BandName = "Gamma0_" + srcBandName;
                    tgtUnit = unit;
                    if (outputSigma0) {
                        sigma0BandName = "Sigma0_" + srcBandName;
                    }
                } else { // amplitude or intensity
                    gamma0BandName = srcBandName.replaceFirst("Beta0", "Gamma0");
                    sigma0BandName = srcBandName.replaceFirst("Beta0", "Sigma0");
                    tgtUnit = Unit.INTENSITY;
                }

                if (targetProduct.getBand(gamma0BandName) == null) {
                    Band tgtBand = targetProduct.addBand(gamma0BandName, ProductData.TYPE_FLOAT32);
                    tgtBand.setUnit(tgtUnit);
                    targetBandToSourceBandMap.put(tgtBand, srcBand);
                }

                if (outputSigma0 && targetProduct.getBand(sigma0BandName) == null) {
                    Band tgtBand = targetProduct.addBand(sigma0BandName, ProductData.TYPE_FLOAT32);
                    tgtBand.setUnit(tgtUnit);
                    targetBandToSourceBandMap.put(tgtBand, srcBand);
                }
            }
        }

        if (targetProduct.getNumBands() == 0) {
            invalidSource = true;
            // Moved the following exception to computeTileStack. Add a dummy band so that computeTileStack get executed
            //throw new OperatorException("TerrainFlattening requires beta0 or T3, C3, C2 as input");
            final Band dummyBand = targetProduct.addBand("dummy", ProductData.TYPE_INT8);
            dummyBand.setUnit(Unit.AMPLITUDE);
        }

        if (outputSimulatedImage) {
            Band tgtBand = targetProduct.addBand("simulatedImage", ProductData.TYPE_FLOAT32);
            tgtBand.setUnit("Ratio");
        }

        targetBands = targetProduct.getBands();
        if (!isPolSar) {
            for (int i = 0; i < targetBands.length; ++i) {
                if (targetBands[i].getUnit().equals(Unit.REAL)) {
                    final String trgBandName = targetBands[i].getName();
                    final int idx = trgBandName.indexOf("_");
                    String suffix = "";
                    if (idx != -1) {
                        suffix = trgBandName.substring(trgBandName.indexOf("_"));
                    }
                    ReaderUtils.createVirtualIntensityBand(
                            targetProduct, targetBands[i], targetBands[i + 1], "Gamma0", suffix);
                }
            }
        }
    }


    /**
     * Called by the framework in order to compute the stack of tiles for the given target bands.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed (same for all rasters in <code>targetRasters</code>).
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException if an error occurs during computation of the target rasters.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm)
            throws OperatorException {

        try {
            if (invalidSource) {
                throw new OperatorException("TerrainFlattening requires beta0 or T3, C3, C2 as input");
            }
            if (!isElevationModelAvailable) {
                getElevationModel();
            }

            final int x0 = targetRectangle.x;
            final int y0 = targetRectangle.y;
            final int w = targetRectangle.width;
            final int h = targetRectangle.height;
            //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

            final OverlapPercentage tileOverlapPercentage = computeTileOverlapPercentage(x0, y0, w, h);

            final double[][] gamma0ReferenceArea = new double[h][w];
            double[][] sigma0ReferenceArea = null;
            if (outputSigma0) {
                sigma0ReferenceArea = new double[h][w];
            }

            final boolean validSimulation = generateSimulatedImage(
                    x0, y0, w, h, tileOverlapPercentage, gamma0ReferenceArea, sigma0ReferenceArea);

            if (!validSimulation) {
                return;
            }

            if (isPolSar) {
                outputNormalizedImageT3(x0, y0, w, h, gamma0ReferenceArea, targetTiles, targetRectangle);
            } else {
                outputNormalizedImageGamma0(x0, y0, w, h, gamma0ReferenceArea, sigma0ReferenceArea, targetTiles, targetRectangle);
            }

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Generate simulated image for normalization.
     *
     * @param x0                  X coordinate of the upper left corner pixel of given tile.
     * @param y0                  Y coordinate of the upper left corner pixel of given tile.
     * @param w                   Width of given tile.
     * @param h                   Height of given tile.
     * @param gamma0ReferenceArea The simulated image for flattened gamma0 generation.
     * @param sigma0ReferenceArea The simulated image for flattened sigma0 generation.
     * @return Boolean flag indicating if the simulation is successful.
     */
    private boolean generateSimulatedImage(final int x0, final int y0, final int w, final int h,
                                           final OverlapPercentage tileOverlapPercentage,
                                           final double[][] gamma0ReferenceArea,
                                           final double[][] sigma0ReferenceArea) {

        try {
            final int ymin = Math.max(y0 - (int) (h * tileOverlapPercentage.tileOverlapUp), 0);
            final int ymax = Math.min(y0 + h + (int) (h * tileOverlapPercentage.tileOverlapDown), sourceImageHeight);
            final int xmin = Math.max(x0 - (int) (w * tileOverlapPercentage.tileOverlapLeft), 0);
            final int xmax = Math.min(x0 + w + (int) (w * tileOverlapPercentage.tileOverlapRight), sourceImageWidth);

            if (reGridMethod) {
                final double[] latLonMinMax = new double[4];
                computeImageGeoBoundary(xmin, xmax, ymin, ymax, latLonMinMax);

                double delta = (double) dem.getDescriptor().getTileWidthInDegrees() /
                        (double) dem.getDescriptor().getTileWidth();

                final double extralat = 20 * delta;
                final double extralon = 20 * delta;

                double latMin = latLonMinMax[0] - extralat;
                double latMax = latLonMinMax[1] + extralat;
                double lonMin = latLonMinMax[2] - extralon;
                double lonMax = latLonMinMax[3] + extralon;

                final PixelPos upperLeft = dem.getIndex(new GeoPos(latMax, lonMin));
                final PixelPos lowerRight = dem.getIndex(new GeoPos(latMin, lonMax));
                final int latMaxIdx = (int) Math.floor(upperLeft.getY());
                final int latMinIdx = (int) Math.ceil(lowerRight.getY());
                final int lonMinIdx = (int) Math.floor(upperLeft.getX());
                final int lonMaxIdx = (int) Math.ceil(lowerRight.getX());

                final GeoPos gpUL = dem.getGeoPos(new PixelPos(lonMinIdx, latMaxIdx));
                final GeoPos gpLR = dem.getGeoPos(new PixelPos(lonMaxIdx, latMinIdx));
                latMin = gpLR.getLat();
                latMax = gpUL.getLat();
                lonMin = gpUL.getLon();
                lonMax = gpLR.getLon();

                delta /= overSamplingFactor;

                final int nLat = (int)Math.round((latMax - latMin) / delta);
                final int nLon = (int)Math.round((lonMax - lonMin) / delta);

                final PositionData posData = new PositionData();
                for (int i = 0; i < nLat; i++) {
                    final double lat = latMax - i*delta;
                    final double[] azimuthIndex = new double[nLon];
                    final double[] rangeIndex = new double[nLon];
                    final double[] gamma0Area = new double[nLon];
                    final double[] elevationAngle = new double[nLon];
                    final boolean[] savePixel = new boolean[nLon];
                    double[] sigma0Area = null;
                    if (outputSigma0) {
                        sigma0Area = new double[nLon];
                    }

                    for (int j = 0; j < nLon; j++) {
                        final double lon = lonMin + j*delta;
                        final Double alt = dem.getElevation(new GeoPos(lat, lon));
                        if (Double.isNaN(alt) || alt.equals(demNoDataValue))
                            continue;

                        if (!getPosition(lat, lon, alt, x0, y0, w, h, posData))
                            continue;

                        final LocalGeometry localGeometry = new LocalGeometry(lat, lon, delta, dem,
                                posData.earthPoint, posData.sensorPos);

                        gamma0Area[j] = computeGamma0Area(localGeometry, demNoDataValue, noDataValue);
                        if (noDataValue.equals(gamma0Area[j]))
                            continue;

                        if (outputSigma0) {
                            sigma0Area[j] = computeSigma0Area(localGeometry, demNoDataValue, noDataValue);
                        }

                        elevationAngle[j] = computeElevationAngle(posData.earthPoint, posData.sensorPos);
                        rangeIndex[j] = posData.rangeIndex;
                        azimuthIndex[j] = posData.azimuthIndex;
                        savePixel[j] = rangeIndex[j] > x0 - 1 && rangeIndex[j] < x0 + w &&
                                azimuthIndex[j] > y0 - 1 && azimuthIndex[j] < y0 + h;
                    }

                    if (orbitOnWest) {
                        // traverse from near range to far range to detect shadowing area
                        double maxElevAngle = 0.0;
                        for (int jj = 0; jj < nLon; jj++) {
                            if (savePixel[jj] && (!detectShadow || elevationAngle[jj] >= maxElevAngle)) {
                                maxElevAngle = elevationAngle[jj];
                                saveGamma0Area(x0, y0, w, h, gamma0Area[jj], azimuthIndex[jj], rangeIndex[jj],
                                        gamma0ReferenceArea);

                                if (outputSigma0) {
                                    saveSigma0Area(x0, y0, w, h, sigma0Area[jj], azimuthIndex[jj], rangeIndex[jj],
                                            sigma0ReferenceArea);
                                }
                            }
                        }

                    } else {
                        // traverse from near range to far range to detect shadowing area
                        double maxElevAngle = 0.0;
                        for (int jj = nLon - 1; jj >= 0; --jj) {
                            if (savePixel[jj] && (!detectShadow || elevationAngle[jj] >= maxElevAngle)) {
                                maxElevAngle = elevationAngle[jj];
                                saveGamma0Area(x0, y0, w, h, gamma0Area[jj], azimuthIndex[jj], rangeIndex[jj],
                                        gamma0ReferenceArea);

                                if (outputSigma0) {
                                    saveSigma0Area(x0, y0, w, h, sigma0Area[jj], azimuthIndex[jj], rangeIndex[jj],
                                            sigma0ReferenceArea);
                                }
                            }
                        }
                    }
                }

            } else {

                final int widthExt = xmax - xmin;
                final int heightExt = ymax - ymin;

                final double[][] localDEM = new double[heightExt + 2][widthExt + 2];
                final TileGeoreferencing tileGeoRef = new TileGeoreferencing(
                        targetProduct, xmin, ymin, widthExt, heightExt);

                final boolean valid = DEMFactory.getLocalDEM(
                        dem, demNoDataValue, demResamplingMethod, tileGeoRef, xmin, ymin, widthExt, heightExt,
                        sourceProduct, true, localDEM);

                if (!valid) {
                    return false;
                }

                final PositionData posData = new PositionData();
                final GeoPos geoPos = new GeoPos();
                for (int y = ymin; y < ymax; y++) {
                    final int yy = y - ymin;

                    final double[] azimuthIndex = new double[widthExt];
                    final double[] rangeIndex = new double[widthExt];
                    final double[] gamma0Area = new double[widthExt];
                    final double[] elevationAngle = new double[widthExt];
                    final boolean[] savePixel = new boolean[widthExt];
                    double[] sigma0Area = null;
                    if (outputSigma0) {
                        sigma0Area = new double[widthExt];
                    }

                    for (int x = xmin; x < xmax; x++) {
                        final int xx = x - xmin;

                        Double alt = localDEM[yy + 1][xx + 1];
                        if (alt.equals(demNoDataValue))
                            continue;

                        tileGeoRef.getGeoPos(x, y, geoPos);
                        if (!geoPos.isValid())
                            continue;

                        double lat = geoPos.lat;
                        double lon = geoPos.lon;
                        if (lon >= 180.0) {
                            lon -= 360.0;
                        }

                        if (!getPosition(lat, lon, alt, x0, y0, w, h, posData))
                            continue;

                        final LocalGeometry localGeometry = new LocalGeometry(
                                xmin, ymin, x, y, tileGeoRef, localDEM, posData.earthPoint, posData.sensorPos);

                        gamma0Area[xx] = computeGamma0Area(localGeometry, demNoDataValue, noDataValue);
                        if (noDataValue.equals(gamma0Area[xx]))
                            continue;

                        if (outputSigma0) {
                            sigma0Area[xx] = computeSigma0Area(localGeometry, demNoDataValue, noDataValue);
                        }

                        elevationAngle[xx] = computeElevationAngle(posData.earthPoint, posData.sensorPos);

                        rangeIndex[xx] = posData.rangeIndex;
                        azimuthIndex[xx] = posData.azimuthIndex;

                        savePixel[xx] = rangeIndex[xx] > x0 - 1 && rangeIndex[xx] < x0 + w &&
                                azimuthIndex[xx] > y0 - 1 && azimuthIndex[xx] < y0 + h;
                    }

                    if (nearRangeOnLeft) {
                        // traverse from near range to far range to detect shadowing area
                        double maxElevAngle = 0.0;
                        for (int i = 0; i < widthExt; i++) {
                            if (savePixel[i] && (!detectShadow || elevationAngle[i] > maxElevAngle)) {
                                maxElevAngle = elevationAngle[i];
                                saveGamma0Area(x0, y0, w, h, gamma0Area[i], azimuthIndex[i], rangeIndex[i],
                                        gamma0ReferenceArea);

                                if (outputSigma0) {
                                    saveSigma0Area(x0, y0, w, h, sigma0Area[i], azimuthIndex[i], rangeIndex[i],
                                            sigma0ReferenceArea);
                                }
                            }
                        }

                    } else {
                        // traverse from near range to far range to detect shadowing area
                        double maxElevAngle = 0.0;
                        for (int i = widthExt - 1; i >= 0; --i) {
                            if (savePixel[i] && (!detectShadow || elevationAngle[i] > maxElevAngle)) {
                                maxElevAngle = elevationAngle[i];
                                saveGamma0Area(x0, y0, w, h, gamma0Area[i], azimuthIndex[i], rangeIndex[i],
                                        gamma0ReferenceArea);

                                if (outputSigma0) {
                                    saveSigma0Area(x0, y0, w, h, sigma0Area[i], azimuthIndex[i], rangeIndex[i],
                                            sigma0ReferenceArea);
                                }
                            }
                        }
                    }
                }
            }

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
        return true;
    }

    private void computeImageGeoBoundary(final int xmin, final int xmax, final int ymin, final int ymax,
                                         double[] latLonMinMax) throws Exception {

        final GeoCoding geoCoding = sourceProduct.getSceneGeoCoding();
        if (geoCoding == null) {
            throw new OperatorException("Product does not contain a geocoding");
        }
        final GeoPos geoPosFirstNear = geoCoding.getGeoPos(new PixelPos(xmin, ymin), null);
        final GeoPos geoPosFirstFar = geoCoding.getGeoPos(new PixelPos(xmax, ymin), null);
        final GeoPos geoPosLastNear = geoCoding.getGeoPos(new PixelPos(xmin, ymax), null);
        final GeoPos geoPosLastFar = geoCoding.getGeoPos(new PixelPos(xmax, ymax), null);

        final double[] lats = {geoPosFirstNear.getLat(), geoPosFirstFar.getLat(), geoPosLastNear.getLat(), geoPosLastFar.getLat()};
        final double[] lons = {geoPosFirstNear.getLon(), geoPosFirstFar.getLon(), geoPosLastNear.getLon(), geoPosLastFar.getLon()};
        double latMin = 90.0;
        double latMax = -90.0;
        for (double lat : lats) {
            if (lat < latMin) {
                latMin = lat;
            }
            if (lat > latMax) {
                latMax = lat;
            }
        }

        double lonMin = 180.0;
        double lonMax = -180.0;
        for (double lon : lons) {
            if (lon < lonMin) {
                lonMin = lon;
            }
            if (lon > lonMax) {
                lonMax = lon;
            }
        }

        latLonMinMax[0] = latMin;
        latLonMinMax[1] = latMax;
        latLonMinMax[2] = lonMin;
        latLonMinMax[3] = lonMax;
    }

    //======================================
    private boolean getPosition(final double lat, final double lon, final double alt,
                                final int x0, final int y0, final int w, final int h,
                                final PositionData data) {

        GeoUtils.geo2xyzWGS84(lat, lon, alt, data.earthPoint);

        final Double zeroDopplerTime = SARGeocoding.getZeroDopplerTime(
                lineTimeInterval, wavelength, data.earthPoint, orbit);

        if (zeroDopplerTime == SARGeocoding.NonValidZeroDopplerTime) {
            return false;
        }

        data.slantRange = SARGeocoding.computeSlantRange(zeroDopplerTime, orbit, data.earthPoint, data.sensorPos);

        data.azimuthIndex = (zeroDopplerTime - firstLineUTC) / lineTimeInterval;

        if (!(data.azimuthIndex >= y0 - 1 && data.azimuthIndex <= y0 + h)) {
            return false;
        }

        if (!srgrFlag) {
            data.rangeIndex = (data.slantRange - nearEdgeSlantRange) / rangeSpacing;
        } else {
            data.rangeIndex = SARGeocoding.computeRangeIndex(
                    srgrFlag, sourceImageWidth, firstLineUTC, lastLineUTC, rangeSpacing,
                    zeroDopplerTime, data.slantRange, nearEdgeSlantRange, srgrConvParams);
        }

        if (!nearRangeOnLeft) {
            data.rangeIndex = sourceImageWidth - 1 - data.rangeIndex;
        }

        return data.rangeIndex >= x0 - 1 && data.rangeIndex <= x0 + w;
    }


    /**
     * Output normalized image.
     *
     * @param x0                  X coordinate of the upper left corner pixel of given tile.
     * @param y0                  Y coordinate of the upper left corner pixel of given tile.
     * @param w                   Width of given tile.
     * @param h                   Height of given tile.
     * @param gamma0ReferenceArea The simulated image for flattened gamma0 generation.
     * @param sigma0ReferenceArea The simulated image for flattened sigma0 generation.
     * @param targetTiles         The current tiles to be computed for each target band.
     * @param targetRectangle     The area in pixel coordinates to be computed.
     */
    private void outputNormalizedImageGamma0(final int x0, final int y0, final int w, final int h,
                                             final double[][] gamma0ReferenceArea, final double[][] sigma0ReferenceArea,
                                             final Map<Band, Tile> targetTiles, final Rectangle targetRectangle) {

        try {
            for (Band tgtBand : targetBands) {
                final Tile targetTile = targetTiles.get(tgtBand);
                final ProductData targetData = targetTile.getDataBuffer();
                final TileIndex tgtIndex = new TileIndex(targetTile);
                final String unit = tgtBand.getUnit();
                final String bandName = tgtBand.getName();

                Band srcBand = null;
                Tile sourceTile = null;
                ProductData sourceData = null;
                TileIndex srcIndex = null;
                if (bandName.contains("Gamma0") || bandName.contains("Sigma0")) {
                    srcBand = targetBandToSourceBandMap.get(tgtBand);
                    sourceTile = getSourceTile(srcBand, targetRectangle);
                    sourceData = sourceTile.getDataBuffer();
                    srcIndex = new TileIndex(sourceTile);
                }

                double[][] simulatedImage;
                if (bandName.contains("Sigma0")) {
                    simulatedImage = sigma0ReferenceArea.clone();
                } else {
                    simulatedImage = gamma0ReferenceArea.clone();
                }

                UnitType unitType = UnitType.AMPLITUDE;
                if (unit.contains(Unit.AMPLITUDE)) {
                    unitType = UnitType.AMPLITUDE;
                } else if (unit.contains(Unit.INTENSITY)) {
                    unitType = UnitType.INTENSITY;
                } else if (unit.contains(Unit.REAL) || unit.contains(Unit.IMAGINARY)) {
                    unitType = UnitType.COMPLEX;
                } else if (unit.contains("Ratio")) {
                    unitType = UnitType.RATIO;
                }

                if (unitType == UnitType.RATIO) {
                    for (int y = y0; y < y0 + h; y++) {
                        final int yy = y - y0;
                        tgtIndex.calculateStride(y);
                        for (int x = x0; x < x0 + w; x++) {
                            final int xx = x - x0;
                            final int tgtIdx = tgtIndex.getIndex(x);
                            double simVal = simulatedImage[yy][xx];
                            if (simVal != noDataValue && simVal != 0.0) {
                                simVal /= beta0;
                                if (isGRD) {
                                    simVal /= FastMath.sin(incidenceAngleTPG.getPixelDouble(x, y) * Constants.DTOR);
                                }
                                targetData.setElemDoubleAt(tgtIdx, simVal);
                            } else {
                                targetData.setElemDoubleAt(tgtIdx, noDataValue);
                            }
                        }
                    }

                } else {

                    double v, simVal;
                    int tgtIdx, srcIdx;
                    for (int y = y0; y < y0 + h; y++) {
                        final int yy = y - y0;
                        tgtIndex.calculateStride(y);
                        srcIndex.calculateStride(y);
                        for (int x = x0; x < x0 + w; x++) {
                            final int xx = x - x0;
                            tgtIdx = tgtIndex.getIndex(x);
                            srcIdx = srcIndex.getIndex(x);
                            simVal = simulatedImage[yy][xx];

                            if (simVal != noDataValue) {
                                simVal /= beta0;
                                if (isGRD) {
                                    simVal /= FastMath.sin(incidenceAngleTPG.getPixelDouble(x, y) * Constants.DTOR);
                                }

                                if (simVal > threshold) {
                                    switch (unitType) {
                                        case AMPLITUDE:
                                            v = sourceData.getElemDoubleAt(srcIdx);
                                            targetData.setElemDoubleAt(tgtIdx, v * v / simVal);
                                            break;
                                        case INTENSITY:
                                            v = sourceData.getElemDoubleAt(srcIdx);
                                            targetData.setElemDoubleAt(tgtIdx, v / simVal);
                                            break;
                                        case COMPLEX:
                                            v = sourceData.getElemDoubleAt(srcIdx);
                                            targetData.setElemDoubleAt(tgtIdx, v / Math.sqrt(simVal));
                                            break;
                                    }
                                }
                            } else {
                                targetData.setElemDoubleAt(tgtIdx, noDataValue);
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    private void outputNormalizedImageT3(final int x0, final int y0, final int w, final int h,
                                         final double[][] gamma0ReferenceArea,
                                         final Map<Band, Tile> targetTiles, final Rectangle targetRectangle) {

        try {
            for (Band tgtBand : targetBands) {
                final Tile targetTile = targetTiles.get(tgtBand);
                final ProductData targetData = targetTile.getDataBuffer();
                final TileIndex tgtIndex = new TileIndex(targetTile);
                final String unit = tgtBand.getUnit();
                final double[][] simulatedImage = gamma0ReferenceArea.clone();

                if (unit.contains("Ratio")) {
                    for (int y = y0; y < y0 + h; y++) {
                        final int yy = y - y0;
                        tgtIndex.calculateStride(y);
                        for (int x = x0; x < x0 + w; x++) {
                            final int xx = x - x0;
                            final int tgtIdx = tgtIndex.getIndex(x);
                            double simVal = simulatedImage[yy][xx];
                            if (simVal != noDataValue && simVal != 0.0) {
                                simVal /= beta0;
                                if (isGRD) {
                                    simVal /= FastMath.sin(incidenceAngleTPG.getPixelDouble(x, y) * Constants.DTOR);
                                }
                                targetData.setElemDoubleAt(tgtIdx, simVal);
                            } else {
                                targetData.setElemDoubleAt(tgtIdx, noDataValue);
                            }
                        }
                    }

                } else {

                    final Band srcBand = targetBandToSourceBandMap.get(tgtBand);
                    final Tile sourceTile = getSourceTile(srcBand, targetRectangle);
                    final ProductData sourceData = sourceTile.getDataBuffer();
                    final TileIndex srcIndex = new TileIndex(sourceTile);

                    double v, simVal;
                    int tgtIdx, srcIdx;
                    for (int y = y0; y < y0 + h; y++) {
                        final int yy = y - y0;
                        tgtIndex.calculateStride(y);
                        srcIndex.calculateStride(y);
                        for (int x = x0; x < x0 + w; x++) {
                            final int xx = x - x0;
                            tgtIdx = tgtIndex.getIndex(x);
                            srcIdx = srcIndex.getIndex(x);
                            simVal = simulatedImage[yy][xx];

                            if (simVal != noDataValue) {
                                simVal /= beta0;
                                if (simVal > threshold) {
                                    v = sourceData.getElemDoubleAt(srcIdx);
                                    targetData.setElemDoubleAt(tgtIdx, v / simVal);
                                }
                            } else {
                                targetData.setElemDoubleAt(tgtIdx, noDataValue);
                            }
                        }
                    }
                }
            }
        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Get elevation model.
     *
     * @throws Exception The exceptions.
     */
    private synchronized void getElevationModel() throws Exception {

        if (isElevationModelAvailable) return;
        try {
            if (externalDEMFile != null) { // if external DEM file is specified by user

                dem = new FileElevationModel(externalDEMFile, demResamplingMethod, externalDEMNoDataValue);
                demNoDataValue = externalDEMNoDataValue;
                demName = externalDEMFile.getPath();

            } else {
                dem = DEMFactory.createElevationModel(demName, demResamplingMethod);
                demNoDataValue = dem.getDescriptor().getNoDataValue();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        isElevationModelAvailable = true;
    }

    private OverlapPercentage computeTileOverlapPercentage(final int x0, final int y0, final int w, final int h)
            throws Exception {

        final PixelPos pixPos = new PixelPos();
        final GeoPos geoPos = new GeoPos();
        PositionData posData = new PositionData();

        double tileOverlapUp = 0.0, tileOverlapDown = 0.0, tileOverlapLeft = 0.0, tileOverlapRight = 0.0;
        for (int y = y0; y < y0 + h; y += 20) {
            for (int x = x0; x < x0 + w; x += 20) {
                pixPos.setLocation(x, y);
                targetGeoCoding.getGeoPos(pixPos, geoPos);
                final double alt = dem.getElevation(geoPos);
                if (noDataValue.equals(alt))
                    continue;

                if (!getPosition(geoPos.lat, geoPos.lon, alt, x0, y0, w, h, posData))
                    continue;

                final double azTileOverlapPercentage = (posData.azimuthIndex - y) / (double) h;
                if (azTileOverlapPercentage > tileOverlapUp) {
                    tileOverlapUp = azTileOverlapPercentage;
                } else if (azTileOverlapPercentage < -tileOverlapDown) {
                    tileOverlapDown = -azTileOverlapPercentage;
                }

                final double rgTileOverlapPercentage = (posData.rangeIndex - x) / (double) w;
                if (posData.rangeIndex != -1) {
                    if (rgTileOverlapPercentage > tileOverlapLeft) {
                        tileOverlapLeft = rgTileOverlapPercentage;
                    } else if (rgTileOverlapPercentage < -tileOverlapRight) {
                        tileOverlapRight = -rgTileOverlapPercentage;
                    }
                }
            }
        }

        tileOverlapUp += 0.1;
        tileOverlapDown += 0.1;
        tileOverlapLeft += 0.1;
        tileOverlapRight += 0.1;

        return new OverlapPercentage(tileOverlapUp, tileOverlapDown, tileOverlapLeft, tileOverlapRight);
    }

    /**
     * Distribute the local illumination area to the 4 adjacent pixels using bi-linear distribution.
     *
     * @param x0                  The x coordinate of the pixel at the upper left corner of current tile.
     * @param y0                  The y coordinate of the pixel at the upper left corner of current tile.
     * @param w                   The tile width.
     * @param h                   The tile height.
     * @param gamma0Area          The illuminated area.
     * @param azimuthIndex        Azimuth pixel index for the illuminated area.
     * @param rangeIndex          Range pixel index for the illuminated area.
     * @param gamma0ReferenceArea Buffer for the simulated image.
     */
    private static void saveGamma0Area(final int x0, final int y0, final int w, final int h, final double gamma0Area,
                                       final double azimuthIndex, final double rangeIndex,
                                       final double[][] gamma0ReferenceArea) {

        final int ia0 = (int) azimuthIndex;
        final int ia1 = ia0 + 1;
        final int ir0 = (int) rangeIndex;
        final int ir1 = ir0 + 1;

        final double wr = rangeIndex - ir0;
        final double wa = azimuthIndex - ia0;
        final double wac = 1 - wa;

        if (ir0 >= x0 && ir0 < x0 + w) {
            final double wrc = 1 - wr;
            if (ia0 >= y0 && ia0 < y0 + h)
                gamma0ReferenceArea[ia0 - y0][ir0 - x0] += wrc * wac * gamma0Area;

            if (ia1 >= y0 && ia1 < y0 + h)
                gamma0ReferenceArea[ia1 - y0][ir0 - x0] += wrc * wa * gamma0Area;
        }

        if (ir1 >= x0 && ir1 < x0 + w) {
            if (ia0 >= y0 && ia0 < y0 + h)
                gamma0ReferenceArea[ia0 - y0][ir1 - x0] += wr * wac * gamma0Area;

            if (ia1 >= y0 && ia1 < y0 + h)
                gamma0ReferenceArea[ia1 - y0][ir1 - x0] += wr * wa * gamma0Area;
        }
    }

    private static void saveSigma0Area(final int x0, final int y0, final int w, final int h, final double sigma0Area,
                                       final double azimuthIndex, final double rangeIndex,
                                       final double[][] sigma0ReferenceArea) {

        final int ia0 = (int) azimuthIndex;
        final int ia1 = ia0 + 1;
        final int ir0 = (int) rangeIndex;
        final int ir1 = ir0 + 1;

        final double wr = rangeIndex - ir0;
        final double wa = azimuthIndex - ia0;
        final double wac = 1 - wa;

        if (ir0 >= x0) {
            final double wrc = 1 - wr;
            if (ia0 >= y0)
                sigma0ReferenceArea[ia0 - y0][ir0 - x0] += wrc * wac * sigma0Area;

            if (ia1 < y0 + h)
                sigma0ReferenceArea[ia1 - y0][ir0 - x0] += wrc * wa * sigma0Area;
        }
        if (ir1 < x0 + w) {
            if (ia0 >= y0)
                sigma0ReferenceArea[ia0 - y0][ir1 - x0] += wr * wac * sigma0Area;

            if (ia1 < y0 + h)
                sigma0ReferenceArea[ia1 - y0][ir1 - x0] += wr * wa * sigma0Area;
        }
    }

    /**
     * Compute elevation angle (in degree).
     *
     * @param earthPoint The coordinate for target on earth surface.
     * @param sensorPos  The coordinate for satellite position.
     * @return The elevation angle in degree.
     */
    private static double computeElevationAngle(final PosVector earthPoint, final PosVector sensorPos) {

        final double xDiff = sensorPos.x - earthPoint.x;
        final double yDiff = sensorPos.y - earthPoint.y;
        final double zDiff = sensorPos.z - earthPoint.z;
        final double slantRange = Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
        final double H2 = sensorPos.x * sensorPos.x + sensorPos.y * sensorPos.y + sensorPos.z * sensorPos.z;
        final double R2 = earthPoint.x * earthPoint.x + earthPoint.y * earthPoint.y + earthPoint.z * earthPoint.z;

        return FastMath.acos((slantRange * slantRange + H2 - R2) / (2 * slantRange * Math.sqrt(H2))) * Constants.RTOD;
    }

    /**
     * Compute local illuminated area for given point.
     *
     * @param lg             Local geometry information.
     * @param demNoDataValue Invalid DEM value.
     * @return The computed local illuminated area.
     */
    private static double computeGamma0Area(
            final LocalGeometry lg, final Double demNoDataValue, final double noDataValue) {

        if (demNoDataValue.equals(lg.t00Height) || demNoDataValue.equals(lg.t01Height) ||
                demNoDataValue.equals(lg.t10Height) || demNoDataValue.equals(lg.t11Height)) {
            return noDataValue;
        }

        final PosVector t00 = new PosVector();
        final PosVector t01 = new PosVector();
        final PosVector t10 = new PosVector();
        final PosVector t11 = new PosVector();

        GeoUtils.geo2xyzWGS84(lg.t00Lat, lg.t00Lon, lg.t00Height, t00);
        GeoUtils.geo2xyzWGS84(lg.t01Lat, lg.t01Lon, lg.t01Height, t01);
        GeoUtils.geo2xyzWGS84(lg.t10Lat, lg.t10Lon, lg.t10Height, t10);
        GeoUtils.geo2xyzWGS84(lg.t11Lat, lg.t11Lon, lg.t11Height, t11);

        // compute slant range direction
        final PosVector s = new PosVector(
                lg.sensorPos.x - lg.centerPoint.x,
                lg.sensorPos.y - lg.centerPoint.y,
                lg.sensorPos.z - lg.centerPoint.z);

        Maths.normalizeVector(s);

        // project points t00, t01, t10 and t11 to the plane that perpendicular to slant range
        final double t00s = Maths.innerProduct(t00, s);
        final double t01s = Maths.innerProduct(t01, s);
        final double t10s = Maths.innerProduct(t10, s);
        final double t11s = Maths.innerProduct(t11, s);

        final double[] p00 = {t00.x - t00s * s.x, t00.y - t00s * s.y, t00.z - t00s * s.z};
        final double[] p01 = {t01.x - t01s * s.x, t01.y - t01s * s.y, t01.z - t01s * s.z};
        final double[] p10 = {t10.x - t10s * s.x, t10.y - t10s * s.y, t10.z - t10s * s.z};
        final double[] p11 = {t11.x - t11s * s.x, t11.y - t11s * s.y, t11.z - t11s * s.z};

        // compute distances between projected points
        final double p00p01 = distance(p00, p01);
        final double p00p10 = distance(p00, p10);
        final double p11p01 = distance(p11, p01);
        final double p11p10 = distance(p11, p10);
        final double p10p01 = distance(p10, p01);

        // compute semi-perimeters of two triangles: p00-p01-p10 and p11-p01-p10
        final double h1 = 0.5 * (p00p01 + p00p10 + p10p01);
        final double h2 = 0.5 * (p11p01 + p11p10 + p10p01);

        // compute the illuminated area
        return Math.sqrt(h1 * (h1 - p00p01) * (h1 - p00p10) * (h1 - p10p01)) +
                Math.sqrt(h2 * (h2 - p11p01) * (h2 - p11p10) * (h2 - p10p01));
    }

    private static double computeSigma0Area(
            final LocalGeometry lg, final Double demNoDataValue, final double noDataValue) {

        if (demNoDataValue.equals(lg.t00Height) || demNoDataValue.equals(lg.t01Height) ||
                demNoDataValue.equals(lg.t10Height) || demNoDataValue.equals(lg.t11Height)) {
            return noDataValue;
        }

        final PosVector t00 = new PosVector();
        final PosVector t01 = new PosVector();
        final PosVector t10 = new PosVector();
        final PosVector t11 = new PosVector();

        GeoUtils.geo2xyzWGS84(lg.t00Lat, lg.t00Lon, lg.t00Height, t00);
        GeoUtils.geo2xyzWGS84(lg.t01Lat, lg.t01Lon, lg.t01Height, t01);
        GeoUtils.geo2xyzWGS84(lg.t10Lat, lg.t10Lon, lg.t10Height, t10);
        GeoUtils.geo2xyzWGS84(lg.t11Lat, lg.t11Lon, lg.t11Height, t11);

        final double[] T00 = {t00.x, t00.y, t00.z};
        final double[] T01 = {t01.x, t01.y, t01.z};
        final double[] T10 = {t10.x, t10.y, t10.z};
        final double[] T11 = {t11.x, t11.y, t11.z};

        // compute distances between projected points
        final double T00T01 = distance(T00, T01);
        final double T00T10 = distance(T00, T10);
        final double T11T01 = distance(T11, T01);
        final double T11T10 = distance(T11, T10);
        final double T10T01 = distance(T10, T01);

        // compute semi-perimeters of two triangles: T00-T01-T10 and T11-T01-T10
        final double h1 = 0.5 * (T00T01 + T00T10 + T10T01);
        final double h2 = 0.5 * (T11T01 + T11T10 + T10T01);

        // compute the illuminated area
        return Math.sqrt(h1 * (h1 - T00T01) * (h1 - T00T10) * (h1 - T10T01)) +
                Math.sqrt(h2 * (h2 - T11T01) * (h2 - T11T10) * (h2 - T10T01));
    }


    private static double distance(final double[] p1, final double[] p2) {
        return Math.sqrt((p1[0] - p2[0]) * (p1[0] - p2[0]) +
                (p1[1] - p2[1]) * (p1[1] - p2[1]) +
                (p1[2] - p2[2]) * (p1[2] - p2[2]));
    }


    public static class LocalGeometry {
        public final double t00Lat;
        public final double t00Lon;
        public final double t00Height;
        public final double t01Lat;
        public final double t01Lon;
        public final double t01Height;
        public final double t10Lat;
        public final double t10Lon;
        public final double t10Height;
        public final double t11Lat;
        public final double t11Lon;
        public final double t11Height;
        public final PosVector sensorPos;
        public final PosVector centerPoint;

        public LocalGeometry(final int x0, final int y0, final int x, final int y,
                             final TileGeoreferencing tileGeoRef, final double[][] localDEM,
                             final PosVector earthPoint, final PosVector sensorPos) {

            final GeoPos geo = new GeoPos();
            final int yy = y - y0 + 1;
            final int xx = x - x0 + 1;

            tileGeoRef.getGeoPos(x, y, geo);
            this.t00Lat = geo.lat;
            this.t00Lon = geo.lon;
            this.t00Height = localDEM[yy][xx];

            tileGeoRef.getGeoPos(x, y - 1, geo);
            this.t01Lat = geo.lat;
            this.t01Lon = geo.lon;
            this.t01Height = localDEM[yy - 1][xx];

            tileGeoRef.getGeoPos(x + 1, y, geo);
            this.t10Lat = geo.lat;
            this.t10Lon = geo.lon;
            this.t10Height = localDEM[yy][xx + 1];

            tileGeoRef.getGeoPos(x + 1, y - 1, geo);
            this.t11Lat = geo.lat;
            this.t11Lon = geo.lon;
            this.t11Height = localDEM[yy - 1][xx + 1];

            this.centerPoint = earthPoint;
            this.sensorPos = sensorPos;
        }

        public LocalGeometry(final double lat, final double lon, final double del, final ElevationModel dem,
                             final PosVector earthPoint, final PosVector sensorPos) throws Exception {

            this.t00Lat = lat;
            this.t00Lon = lon;
            this.t00Height = dem.getElevation(new GeoPos(t00Lat, t00Lon));

            this.t01Lat = lat - del;
            this.t01Lon = lon;
            this.t01Height = dem.getElevation(new GeoPos(t01Lat, t01Lon));

            this.t10Lat = lat;
            this.t10Lon = lon + del;
            this.t10Height = dem.getElevation(new GeoPos(t10Lat, t10Lon));

            this.t11Lat = lat - del;
            this.t11Lon = lon + del;
            this.t11Height = dem.getElevation(new GeoPos(t11Lat, t11Lon));

            this.centerPoint = earthPoint;
            this.sensorPos = sensorPos;
        }
    }

    private static class PositionData {
        final PosVector earthPoint = new PosVector();
        final PosVector sensorPos = new PosVector();
        double azimuthIndex;
        double rangeIndex;
        double slantRange;
    }

    private static class OverlapPercentage {
        final double tileOverlapUp;
        final double tileOverlapDown;
        final double tileOverlapLeft;
        final double tileOverlapRight;

        public OverlapPercentage(final double tileOverlapUp, final double tileOverlapDown,
                                 final double tileOverlapLeft, final double tileOverlapRight) {
            this.tileOverlapUp = tileOverlapUp;
            this.tileOverlapDown = tileOverlapDown;
            this.tileOverlapLeft = tileOverlapLeft;
            this.tileOverlapRight = tileOverlapRight;
        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.snap.core.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(TerrainFlatteningOp.class);
        }
    }
}
