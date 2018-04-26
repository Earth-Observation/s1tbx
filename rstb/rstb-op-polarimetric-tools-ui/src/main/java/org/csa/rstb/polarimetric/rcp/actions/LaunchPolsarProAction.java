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
package org.csa.rstb.polarimetric.rcp.actions;

import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.io.SnapFileFilter;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.util.Dialogs;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.util.prefs.Preferences;

@ActionID(category = "Raster", id = "LaunchPolsarProAction" )
@ActionRegistration(
        displayName = "#CTL_LaunchPolsarProAction_MenuText",
        popupText = "#CTL_LaunchPolsarProAction_MenuText",
        lazy = true
)
@ActionReference(path = "Menu/Radar/Polarimetric", position = 50, separatorAfter = 51)
@NbBundle.Messages({
        "CTL_LaunchPolsarProAction_MenuText=Launch PolSARPro",
        "CTL_LaunchPolsarProAction_ShortDescription=Start PolSARPro application"
})
/**
 * This action launches PolSARPro
 */
public class LaunchPolsarProAction extends AbstractAction {

    private final static String PolsarProPathStr = "external.polsarpro.path";
    private final static String TCLPathStr = "external.TCL.path";
    private final static String LAST_POLSARPRO_DIR_KEY = "snap.polsarpro.dir";

    /**
     * Launches PolSARPro
     * Invoked when a command action is performed.
     *
     * @param event the command event.
     */
    @Override
    public void actionPerformed(ActionEvent event) {
        final Preferences pref = SnapApp.getDefault().getPreferences();

        // find tcl wish
        File tclFile = new File(pref.get(TCLPathStr, ""));

        if (!tclFile.exists()) {
            tclFile = findTCLWish();
            if (tclFile.exists())
                pref.put(TCLPathStr, tclFile.getAbsolutePath());
        }

        // find polsar pro
        File polsarProFile = new File(pref.get(PolsarProPathStr, ""));

        if (!polsarProFile.exists()) {
            polsarProFile = findPolsarPro();
        }
        if (!polsarProFile.exists()) {
            // ask for location
            polsarProFile = Dialogs.requestFileForOpen("PolSARPro Location", false,
                                                       new SnapFileFilter("PolSARPro TCL", new String[]{".tcl"}, "PolSARPro"),
                                                       LAST_POLSARPRO_DIR_KEY);
        }
        if (polsarProFile.exists()) {
            externalExecute(polsarProFile, tclFile);

            // save location
            pref.put(PolsarProPathStr, polsarProFile.getAbsolutePath());
        }
    }

    private static void externalExecute(final File prog, final File tclWishFile) {

        final Thread worker = new Thread() {

            @Override
            public void run() {
                try {

                    if (tclWishFile.exists()) {
                        if (prog.exists()) {
                            String command = tclWishFile.getAbsolutePath() + ' ' + '"' + prog.getAbsolutePath() + '"';
                            SystemUtils.LOG.info("Launching PolSARPro: " + command);
                            final Process proc = Runtime.getRuntime().exec(command, null, new File(prog.getParent()));

                            outputTextBuffers(new BufferedReader(new InputStreamReader(proc.getInputStream())));
                            outputTextBuffers(new BufferedReader(new InputStreamReader(proc.getErrorStream())));
                        }
                        else {
                            Dialogs.showError("The file: " + prog.getAbsolutePath() + " does not exist.");
                        }
                    }
                    else {
                        Dialogs.showError("Cannot find TCL wish.exe to launch PolSARPro");
                    }
                } catch (Exception e) {
                    Dialogs.showError("Unable to launch PolSARPro:" + e.getMessage());
                }
            }
        };
        worker.start();
    }

    private static File findTCLWish() {
        File progFiles = new File("C:\\Program Files (x86)\\TCL\\bin");
        if (!progFiles.exists())
            progFiles = new File("C:\\Program Files\\TCL\\bin");
        if (!progFiles.exists())
            progFiles = new File("C:\\TCL\\bin");
        if (progFiles.exists()) {
            final File[] files = progFiles.listFiles();
            if(files != null) {
                for (File file : files) {
                    final String name = file.getName().toLowerCase();
                    if (name.equals("wish.exe")) {
                        return file;
                    }
                }
            }
        }
        return new File("");
    }

    private static File findPolsarPro() {
        File progFiles = new File("C:\\Program Files (x86)");
        if (!progFiles.exists())
            progFiles = new File("C:\\Program Files");
        if (progFiles.exists()) {
            final File[] progs = progFiles.listFiles(new PolsarFileFilter());
            if(progs != null) {
                for (File prog : progs) {
                    final File[] fileList = prog.listFiles(new PolsarFileFilter());
                    if(fileList != null) {
                        for (File file : fileList) {
                            if (file.getName().toLowerCase().endsWith("tcl")) {
                                return file;
                            }
                        }
                    }
                }
            }
        }
        return new File("");
    }

    private static class PolsarFileFilter implements FilenameFilter {

        public boolean accept(File dir, String name) {

            return name.toLowerCase().startsWith("polsar");
        }
    }

    private static boolean outputTextBuffers(BufferedReader in) throws IOException {
        char c;
        boolean hasData = false;
        while ((c = (char) in.read()) != -1 && c != 65535) {
            //errStr += c;
            System.out.print(c);
            hasData = true;
        }
        return hasData;
    }
}
