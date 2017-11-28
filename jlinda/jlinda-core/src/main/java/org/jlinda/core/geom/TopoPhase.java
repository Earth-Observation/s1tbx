package org.jlinda.core.geom;

import org.esa.snap.core.util.SystemUtils;
import org.jlinda.core.*;
import org.jlinda.core.delaunay.TriangleInterpolator;

import java.util.Arrays;
import java.util.logging.Logger;

public class TopoPhase {

    //// logger
    static Logger logger = SystemUtils.LOG;

    private Orbit masterOrbit;   // master
    private SLCImage masterMeta; // master
    private Orbit slaveOrbit;    // slave
    private SLCImage slaveMeta;  // slave

    private Window tileWindow;    // buffer/tile coordinates

    private DemTile dem;           // demTileData
    public double[][] demPhase;
    public double[][] elevation;
    public double[][] latitude;
    public double[][] longitude;

    private double[][] demRadarCode_x;
    private double[][] demRadarCode_y;
    private double[][] demRadarCode_phase; // interpolated grid
    private double[][] demElevation;
    private double[][] demLatitude;
    private double[][] demLongitude;

    private int nRows;
    private int nCols;

    private double rngAzRatio = 0;
    private boolean isBiStaticStack = false;
    private static final double invalidIndex = -9999.0;

    public TopoPhase(SLCImage masterMeta, Orbit masterOrbit, SLCImage slaveMeta, Orbit slaveOrbit, Window window,
                     DemTile demTile) throws Exception {
        this.masterOrbit = masterOrbit;
        this.masterMeta = masterMeta;
        this.slaveOrbit = slaveOrbit;
        this.slaveMeta = slaveMeta;
        this.tileWindow = window;
        this.dem = demTile;

        nRows = dem.data.length;
        nCols = dem.data[0].length;

        isBiStaticStack = masterMeta.isBiStaticStack;
    }

    public void setMasterOrbit(Orbit masterOrbit) {
        this.masterOrbit = masterOrbit;
    }

    public void setMasterMeta(SLCImage masterMeta) {
        this.masterMeta = masterMeta;
    }

    public void setSlaveOrbit(Orbit slaveOrbit) {
        this.slaveOrbit = slaveOrbit;
    }

    public void setSlaveMeta(SLCImage slaveMeta) {
        this.slaveMeta = slaveMeta;
    }

    public void setWindow(Window window) {
        this.tileWindow = window;
    }

    public double[][] getDemRadarCode_phase() {
        return demRadarCode_phase;
    }

    public double[][] getDemRadarCode_x() {
        return demRadarCode_x;
    }

    public double[][] getDemRadarCode_y() {
        return demRadarCode_y;
    }

    public double getRngAzRatio() {
        return rngAzRatio;
    }

    public void setRngAzRatio(double rngAzRatio) {
        this.rngAzRatio = rngAzRatio;
    }

    public void radarCode() throws Exception {

        //logger.info("Converting DEM to radar system for this tile.");

        demRadarCode_x = new double[nRows][nCols];
        demRadarCode_y = new double[nRows][nCols];
        demRadarCode_phase = new double[nRows][nCols];
        demElevation = new double[nRows][nCols];
        demLatitude = new double[nRows][nCols];
        demLongitude = new double[nRows][nCols];

        final int nPoints = nRows * nCols;
        final boolean onlyTopoRefPhase = true;

        //logger.info("Number of points in DEM: " + nPoints);

        double masterMin4piCDivLam = (-4 * Math.PI * Constants.SOL) / masterMeta.getRadarWavelength();
        double slaveMin4piCDivLam = (-4 * Math.PI * Constants.SOL) / slaveMeta.getRadarWavelength();

        double phi, lambda, height, line, pix, ref_phase;

        final double upperLeftPhi = dem.lat0;// - dem.indexPhi0DEM * dem.latitudeDelta;
        final double upperLeftLambda = dem.lon0;// + dem.indexLambda0DEM * dem.longitudeDelta;

        Point pointOnDem;
        Point slaveTime;

        phi = upperLeftPhi;
        for (int i = 0; i < nRows; i++) {

//            if ((i % 100) == 0) {
//                logger.info("Radarcoding DEM line: " + i + " (" + Math.floor(.5 + (100. * (double) i / (double) (nRows))) + "%");
//            }

            lambda = upperLeftLambda;
            double[] heightArray = dem.data[i];

            for (int j = 0; j < nCols; j++) {

                height = heightArray[j];
                demElevation[i][j] = height;
                demLatitude[i][j] = phi;
                demLongitude[i][j] = lambda;

                if (height != dem.noDataValue) {

                    double[] phi_lam_height = {phi, lambda, height};
                    Point sarPoint = masterOrbit.ell2lp(phi_lam_height, masterMeta);

                    line = sarPoint.y;
                    pix = sarPoint.x;

                    demRadarCode_y[i][j] = line;
                    demRadarCode_x[i][j] = pix;

                    pointOnDem = Ellipsoid.ell2xyz(phi_lam_height);
                    slaveTime = slaveOrbit.xyz2t(pointOnDem, slaveMeta);
/*
                if (outH2PH == true) {

                    // compute h2ph factor
                    Point masterSatPos = masterOrbit.getXYZ(masterTime.y);
                    Point slaveSatpOS = slaveOrbit.getXYZ(slaveTime.y);
                    double B = masterSatPos.distance(slaveSatpOS);
                    double Bpar = Constants.SOL * (masterTime.x - masterTime.y);
                    Point rangeDistToMaster = masterSatPos.min(pointOnDem);
                    Point rangeDistToSlave = slaveSatpOS.min(pointOnDem);
                    // double thetaMaster = masterSatPos.angle(rangeDistToMaster);  // look angle - not needed
                    double thetaMaster = pointOnDem.angle(rangeDistToMaster); // incidence angle
                    double thetaSlave = pointOnDem.angle(rangeDistToSlave); // incidence angle slave
                    double Bperp = (thetaMaster > thetaSlave) ? // sign ok
                            Math.sqrt(MathUtils.sqr(B) - MathUtils.sqr(Bpar))
                            : -Math.sqrt(MathUtils.sqr(B) - MathUtils.sqr(Bpar));

                    h2phArray[i][j] = Bperp / (masterTime.x * Constants.SOL * Math.sin(thetaMaster));
                }
*/
                    // do not include flat earth phase
                    if (onlyTopoRefPhase) {
                        Point masterXYZPos = masterOrbit.lp2xyz(line, pix, masterMeta);
                        Point flatEarthTime = slaveOrbit.xyz2t(masterXYZPos, slaveMeta);
                        if (isBiStaticStack) {
                            ref_phase = slaveMin4piCDivLam * (flatEarthTime.x - slaveTime.x) * 0.5;
                        } else {
                            ref_phase = slaveMin4piCDivLam * (flatEarthTime.x - slaveTime.x);
                        }
                    } else {
                        // include flatearth, ref.pha = phi_topo+phi_flatearth
                        ref_phase = masterMin4piCDivLam * masterMeta.pix2tr(pix) - slaveMin4piCDivLam * slaveTime.x;
                    }

                    demRadarCode_phase[i][j] = ref_phase;

                } else {

                    double[] phi_lam_height = {phi, lambda, 0};
                    Point sarPoint = masterOrbit.ell2lp(phi_lam_height, masterMeta);

                    line = sarPoint.y;
                    pix = sarPoint.x;

                    demRadarCode_y[i][j] = invalidIndex;//line;
                    demRadarCode_x[i][j] = invalidIndex;//pix;
                    demRadarCode_phase[i][j] = 0;
                }

                lambda += dem.longitudeDelta;
            }
            phi -= dem.latitudeDelta;
        }
    }


    public void calculateScalingRatio() throws Exception {

/*
        // TTODO: simplify this! Tiles are _simpler_ then doris.core buffers!
        final Window masterWin = masterMeta.getCurrentWindow();
        final Window tileWin = (Window) masterWin.clone();

        final int mlL = masterMeta.getMlAz();
        final int mlP = masterMeta.getMlRg();
        final long nLinesMl = masterWin.lines() / mlL; // ifg lines when mlL = 1 (no multilooking)
        final long nPixelsMl = masterWin.pixels() / mlP;

        double ifgLineLo = tileWin.linelo;
        double ifgLineHi = tileWin.pixlo;

        final double veryFirstLine = ifgLineLo + ((double) mlL - 1.0) / 2.0;
        final double veryLastLine = veryFirstLine + (double)((nLinesMl - 1) * mlL);
        final double firstPixel = ifgLineHi + ((double) mlP - 1.0) / 2.0;
        final double lastPixel = firstPixel + (double)((nPixelsMl - 1) * mlP);

        //Determine range-azimuth spacing ratio, needed for proper triangulation
        Point p1 = masterOrbit.lp2xyz(veryFirstLine, firstPixel, masterMeta);
        Point p2 = masterOrbit.lp2xyz(veryFirstLine, lastPixel, masterMeta);
        Point p3 = masterOrbit.lp2xyz(veryLastLine, firstPixel, masterMeta);
        Point p4 = masterOrbit.lp2xyz(veryLastLine, lastPixel, masterMeta);

        final double rangeSpacing = ((p1.min(p2)).norm() + (p3.min(p4)).norm()) / 2
                / (lastPixel - firstPixel);
        final double aziSpacing = ((p1.min(p3)).norm() + (p2.min(p4)).norm()) / 2
                / (veryLastLine - veryFirstLine);
        final double rangeAzRatio = rangeSpacing / aziSpacing;

*/

        //Determine range-azimuth spacing ratio, needed for proper triangulation
        final long firstLine = tileWindow.linelo;
        final long lastLine = tileWindow.linehi;
        final long firstPixel = tileWindow.pixlo;
        final long lastPixel = tileWindow.pixhi;
        Point p1 = masterOrbit.lp2xyz(firstLine, firstPixel, masterMeta);
        Point p2 = masterOrbit.lp2xyz(firstLine, lastPixel, masterMeta);
        Point p3 = masterOrbit.lp2xyz(lastLine, firstPixel, masterMeta);
        Point p4 = masterOrbit.lp2xyz(lastLine, lastPixel, masterMeta);


        final double rangeSpacing = ((p1.min(p2)).norm() + (p3.min(p4)).norm()) / 2
                / (lastPixel - firstPixel);
        final double aziSpacing = ((p1.min(p3)).norm() + (p2.min(p4)).norm()) / 2
                / (lastLine - firstLine);
        rngAzRatio = rangeSpacing / aziSpacing;

        logger.fine("Interferogram azimuth spacing: " + aziSpacing);
        logger.fine("Interferogram range spacing: " + rangeSpacing);
        logger.fine("Range-azimuth spacing ratio: " + rngAzRatio);

    }

    public void gridData(boolean includeDEM, boolean includeLatLon) throws Exception {
        if (rngAzRatio == 0) {
            calculateScalingRatio();
        }
        int mlAz = masterMeta.getMlAz();
        int mlRg = masterMeta.getMlRg();
        int offset = 0;
        demPhase = new double[(int) tileWindow.lines()][(int) tileWindow.pixels()];

        TriangleInterpolator.ZData[] data;
        if(includeDEM || includeLatLon) {
            final int nLines = (int) tileWindow.lines();
            final int nPixels = (int) tileWindow.pixels();
            if (includeDEM) {
                elevation = new double[nLines][nPixels];
                for (double[] row : elevation) {
                    Arrays.fill(row, dem.noDataValue);
                }
            }
            if (includeLatLon) {
                latitude = new double[nLines][nPixels];
                longitude = new double[nLines][nPixels];
                for (double[] row : latitude) {
                    Arrays.fill(row, Double.NaN);
                }
                for (double[] row : longitude) {
                    Arrays.fill(row, Double.NaN);
                }
            }

            if (includeDEM && includeLatLon) {
                data = new TriangleInterpolator.ZData[]{
                        new TriangleInterpolator.ZData(demRadarCode_phase, demPhase),
                        new TriangleInterpolator.ZData(demElevation, elevation),
                        new TriangleInterpolator.ZData(demLatitude, latitude),
                        new TriangleInterpolator.ZData(demLongitude, longitude)
                };
            } else if (includeDEM) {
                data = new TriangleInterpolator.ZData[]{
                        new TriangleInterpolator.ZData(demRadarCode_phase, demPhase),
                        new TriangleInterpolator.ZData(demElevation, elevation),
                };
            }  else { // includeLatLon must be true
                data = new TriangleInterpolator.ZData[]{
                        new TriangleInterpolator.ZData(demRadarCode_phase, demPhase),
                        new TriangleInterpolator.ZData(demLatitude, latitude),
                        new TriangleInterpolator.ZData(demLongitude, longitude)
                };
            }
        } else {
            data = new TriangleInterpolator.ZData[] {
                    new TriangleInterpolator.ZData(demRadarCode_phase, demPhase)
            };
        }

        TriangleInterpolator.gridDataLinear(demRadarCode_y, demRadarCode_x, data,
                tileWindow, rngAzRatio, mlAz, mlRg, invalidIndex, offset);
    }
}
