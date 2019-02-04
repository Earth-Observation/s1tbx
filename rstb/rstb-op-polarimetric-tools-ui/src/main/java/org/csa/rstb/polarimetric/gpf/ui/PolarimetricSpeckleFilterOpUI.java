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
package org.csa.rstb.polarimetric.gpf.ui;

import org.csa.rstb.polarimetric.gpf.PolarimetricSpeckleFilterOp;
import org.csa.rstb.polarimetric.gpf.specklefilters.LeeSigma;
import org.esa.snap.engine_utilities.gpf.FilterWindow;
import org.esa.snap.graphbuilder.gpf.ui.BaseOperatorUI;
import org.esa.snap.graphbuilder.gpf.ui.UIValidation;
import org.esa.snap.graphbuilder.rcp.utils.DialogUtils;
import org.esa.snap.ui.AppContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Map;

public class PolarimetricSpeckleFilterOpUI extends BaseOperatorUI {

    private final JComboBox<String> filter = new JComboBox(new String[]{PolarimetricSpeckleFilterOp.BOXCAR_SPECKLE_FILTER,
            PolarimetricSpeckleFilterOp.IDAN_FILTER,
            PolarimetricSpeckleFilterOp.REFINED_LEE_FILTER,
            PolarimetricSpeckleFilterOp.LEE_SIGMA_FILTER,
            PolarimetricSpeckleFilterOp.NON_LOCAL_FILTER});

    private final JComboBox<String> numLooks = new JComboBox(new String[]{PolarimetricSpeckleFilterOp.NUM_LOOKS_1,
            PolarimetricSpeckleFilterOp.NUM_LOOKS_2,
            PolarimetricSpeckleFilterOp.NUM_LOOKS_3,
            PolarimetricSpeckleFilterOp.NUM_LOOKS_4});

    private final JComboBox<String> windowSize = new JComboBox(new String[]{
            FilterWindow.SIZE_5x5, FilterWindow.SIZE_7x7, FilterWindow.SIZE_9x9, FilterWindow.SIZE_11x11,
            FilterWindow.SIZE_13x13, FilterWindow.SIZE_15x15, FilterWindow.SIZE_17x17});

    private final JComboBox<String> targetWindowSize = new JComboBox(new String[]{
            FilterWindow.SIZE_3x3,
            FilterWindow.SIZE_5x5});

    private final JComboBox<String> sigmaStr = new JComboBox(new String[]{LeeSigma.SIGMA_50_PERCENT,
            LeeSigma.SIGMA_60_PERCENT,
            LeeSigma.SIGMA_70_PERCENT,
            LeeSigma.SIGMA_80_PERCENT,
            LeeSigma.SIGMA_90_PERCENT});

    private final JComboBox<String> searchWindowSize = new JComboBox(new String[]{"3", "5", "7", "9", "11", "13", "15",
            "17", "19", "21", "23", "25"});

    private final JComboBox<String> patchSize = new JComboBox(new String[]{"3", "5", "7", "9", "11"});

    private final JComboBox<String> scaleSize = new JComboBox(new String[]{"0", "1", "2"});

    private static final JLabel filterLabel = new JLabel("Speckle Filter:");
    private static final JLabel filterSizeLabel = new JLabel("Filter Size:   ");
    private static final JLabel numLooksLabel = new JLabel("Number of Looks:");
    private static final JLabel windowSizeLabel = new JLabel("Window Size:");
    private static final JLabel filterWindowSizeLabel = new JLabel("Filter Window Size:");
    private static final JLabel targetWindowSizeLabel = new JLabel("Target Window Size:");
    private static final JLabel anSizeLabel = new JLabel("Adaptive Neighbourhood Size:");
    private static final JLabel sigmaStrLabel = new JLabel("Sigma:");
    private static final JLabel searchWindowSizeLabel = new JLabel("Search Window Size:");
    private static final JLabel patchSizeLabel = new JLabel("Patch Size:");
    private static final JLabel scaleSizeLabel = new JLabel("Scale Size:");

    private final JTextField filterSize = new JTextField("");
    private final JTextField anSize = new JTextField("");

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final JComponent panel = createPanel();

        filter.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent event) {
                updateFilterSelection();
            }
        });
        updateFilterSelection();

        initParameters();

        return panel;
    }

    @Override
    public void initParameters() {

        filter.setSelectedItem(paramMap.get("filter"));
        filterSize.setText(String.valueOf(paramMap.get("filterSize")));
        numLooks.setSelectedItem(paramMap.get("numLooksStr"));
        windowSize.setSelectedItem(paramMap.get("windowSize"));
        targetWindowSize.setSelectedItem(paramMap.get("targetWindowSizeStr"));
        sigmaStr.setSelectedItem(paramMap.get("sigmaStr"));
        anSize.setText(String.valueOf(paramMap.get("anSize")));
        searchWindowSize.setSelectedItem(paramMap.get("searchWindowSizeStr"));
        patchSize.setSelectedItem(paramMap.get("patchSizeStr"));
        scaleSize.setSelectedItem(paramMap.get("scaleSizeStr"));
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(UIValidation.State.OK, "");
    }

    @Override
    public void updateParameters() {

        paramMap.put("filter", filter.getSelectedItem());
        paramMap.put("filterSize", Integer.parseInt(filterSize.getText()));
        paramMap.put("numLooksStr", numLooks.getSelectedItem());
        paramMap.put("windowSize", windowSize.getSelectedItem());
        paramMap.put("targetWindowSizeStr", targetWindowSize.getSelectedItem());
        paramMap.put("sigmaStr", sigmaStr.getSelectedItem());
        paramMap.put("anSize", Integer.parseInt(anSize.getText()));
        paramMap.put("searchWindowSizeStr", searchWindowSize.getSelectedItem());
        paramMap.put("patchSizeStr", patchSize.getSelectedItem());
        paramMap.put("scaleSizeStr", scaleSize.getSelectedItem());
    }

    private JComponent createPanel() {

        final JPanel contentPane = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = DialogUtils.createGridBagConstraints();

        DialogUtils.addComponent(contentPane, gbc, filterLabel, filter);

        int savedY = ++gbc.gridy;
        DialogUtils.addComponent(contentPane, gbc, filterSizeLabel, filterSize);
        DialogUtils.enableComponents(filterSizeLabel, filterSize, true);

        gbc.gridy = savedY + 1;
        DialogUtils.addComponent(contentPane, gbc, numLooksLabel, numLooks);
        DialogUtils.enableComponents(numLooksLabel, numLooks, false);

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, windowSizeLabel, windowSize);
        DialogUtils.enableComponents(windowSizeLabel, windowSize, false);

        DialogUtils.addComponent(contentPane, gbc, anSizeLabel, anSize);
        DialogUtils.enableComponents(anSizeLabel, anSize, false);

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, sigmaStrLabel, sigmaStr);
        DialogUtils.enableComponents(sigmaStrLabel, sigmaStr, false);

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, targetWindowSizeLabel, targetWindowSize);
        DialogUtils.enableComponents(targetWindowSizeLabel, targetWindowSize, false);

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, searchWindowSizeLabel, searchWindowSize);
        DialogUtils.enableComponents(searchWindowSizeLabel, searchWindowSize, false);

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, patchSizeLabel, patchSize);
        DialogUtils.enableComponents(patchSizeLabel, patchSize, false);

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, scaleSizeLabel, scaleSize);
        DialogUtils.enableComponents(scaleSizeLabel, scaleSize, false);

        DialogUtils.fillPanel(contentPane, gbc);

        return contentPane;
    }

    private void updateFilterSelection() {
        final String item = (String) filter.getSelectedItem();
        switch (item) {
            case PolarimetricSpeckleFilterOp.REFINED_LEE_FILTER:
                DialogUtils.enableComponents(numLooksLabel, numLooks, true);
                DialogUtils.enableComponents(windowSizeLabel, windowSize, true);
                DialogUtils.enableComponents(filterSizeLabel, filterSize, false);
                DialogUtils.enableComponents(anSizeLabel, anSize, false);
                DialogUtils.enableComponents(sigmaStrLabel, sigmaStr, false);
                DialogUtils.enableComponents(targetWindowSizeLabel, targetWindowSize, false);
                DialogUtils.enableComponents(searchWindowSizeLabel, searchWindowSize, false);
                DialogUtils.enableComponents(patchSizeLabel, patchSize, false);
                DialogUtils.enableComponents(scaleSizeLabel, scaleSize, false);
                break;
            case PolarimetricSpeckleFilterOp.IDAN_FILTER:
                DialogUtils.enableComponents(numLooksLabel, numLooks, true);
                DialogUtils.enableComponents(anSizeLabel, anSize, true);
                DialogUtils.enableComponents(windowSizeLabel, windowSize, false);
                DialogUtils.enableComponents(filterSizeLabel, filterSize, false);
                DialogUtils.enableComponents(sigmaStrLabel, sigmaStr, false);
                DialogUtils.enableComponents(targetWindowSizeLabel, targetWindowSize, false);
                DialogUtils.enableComponents(searchWindowSizeLabel, searchWindowSize, false);
                DialogUtils.enableComponents(patchSizeLabel, patchSize, false);
                DialogUtils.enableComponents(scaleSizeLabel, scaleSize, false);
                break;
            case PolarimetricSpeckleFilterOp.LEE_SIGMA_FILTER:
                DialogUtils.enableComponents(numLooksLabel, numLooks, true);
                DialogUtils.enableComponents(sigmaStrLabel, sigmaStr, true);
                DialogUtils.enableComponents(targetWindowSizeLabel, targetWindowSize, true);
                DialogUtils.enableComponents(anSizeLabel, anSize, false);
                DialogUtils.enableComponents(windowSizeLabel, windowSize, true);
                DialogUtils.enableComponents(filterSizeLabel, filterSize, false);
                DialogUtils.enableComponents(searchWindowSizeLabel, searchWindowSize, false);
                DialogUtils.enableComponents(patchSizeLabel, patchSize, false);
                DialogUtils.enableComponents(scaleSizeLabel, scaleSize, false);
                break;
            case PolarimetricSpeckleFilterOp.NON_LOCAL_FILTER:
                DialogUtils.enableComponents(numLooksLabel, numLooks, true);
                DialogUtils.enableComponents(anSizeLabel, anSize, false);
                DialogUtils.enableComponents(windowSizeLabel, windowSize, false);
                DialogUtils.enableComponents(filterSizeLabel, filterSize, false);
                DialogUtils.enableComponents(sigmaStrLabel, sigmaStr, false);
                DialogUtils.enableComponents(filterSizeLabel, filterSize, false);
                DialogUtils.enableComponents(searchWindowSizeLabel, searchWindowSize, true);
                DialogUtils.enableComponents(patchSizeLabel, patchSize, true);
                DialogUtils.enableComponents(scaleSizeLabel, scaleSize, true);
                break;
            default:  // boxcar
                DialogUtils.enableComponents(numLooksLabel, numLooks, false);
                DialogUtils.enableComponents(windowSizeLabel, windowSize, false);
                DialogUtils.enableComponents(filterSizeLabel, filterSize, true);
                DialogUtils.enableComponents(anSizeLabel, anSize, false);
                DialogUtils.enableComponents(sigmaStrLabel, sigmaStr, false);
                DialogUtils.enableComponents(targetWindowSizeLabel, targetWindowSize, false);
                DialogUtils.enableComponents(searchWindowSizeLabel, searchWindowSize, false);
                DialogUtils.enableComponents(patchSizeLabel, patchSize, false);
                DialogUtils.enableComponents(scaleSizeLabel, scaleSize, false);
                break;
        }
    }

}
