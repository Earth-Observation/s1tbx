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
package org.csa.rstb.classification.gpf.ui;

import org.esa.snap.graphbuilder.gpf.ui.BaseOperatorUI;
import org.esa.snap.graphbuilder.gpf.ui.UIValidation;
import org.esa.snap.graphbuilder.rcp.utils.DialogUtils;
import org.esa.snap.rcp.util.Dialogs;
import org.esa.snap.ui.AppContext;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Map;

public class SupervisedWishartClassificationOpUI extends BaseOperatorUI {

    private final JLabel trainingDataSetLabel = new JLabel("Training Data Set:");
    private final JTextField trainingDataSet = new JTextField("");
    private final JButton trainingDataSetBrowseButton = new JButton("...");
    private final JLabel windowSizeLabel = new JLabel("Window Size:   ");
    private final JTextField windowSize = new JTextField("");

    public static final String LAST_TRAINING_DIR = "rstb.trainingDir";

    @Override
    public JComponent CreateOpTab(String operatorName, Map<String, Object> parameterMap, AppContext appContext) {

        initializeOperatorUI(operatorName, parameterMap);
        final JComponent panel = createPanel();
        initParameters();

        trainingDataSet.setColumns(30);
        trainingDataSetBrowseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final File file = Dialogs.requestFileForOpen("Training Data Set", false, null, LAST_TRAINING_DIR);
                trainingDataSet.setText(file.getAbsolutePath());
            }
        });

        return panel;
    }

    @Override
    public void initParameters() {

        final File dataSetFile = (File) paramMap.get("trainingDataSet");
        if (dataSetFile != null) {
            trainingDataSet.setText(dataSetFile.getAbsolutePath());
        }
        windowSize.setText(String.valueOf(paramMap.get("windowSize")));
    }

    @Override
    public UIValidation validateParameters() {

        return new UIValidation(UIValidation.State.OK, "");
    }

    @Override
    public void updateParameters() {

        final String dataSetStr = trainingDataSet.getText();
        if (!dataSetStr.isEmpty()) {
            paramMap.put("trainingDataSet", new File(dataSetStr));
        }
        paramMap.put("windowSize", Integer.parseInt(windowSize.getText()));
    }

    private JComponent createPanel() {

        final JPanel contentPane = new JPanel(new GridBagLayout());
        final GridBagConstraints gbc = DialogUtils.createGridBagConstraints();

        gbc.gridx = 0;
        DialogUtils.addComponent(contentPane, gbc, trainingDataSetLabel, trainingDataSet);
        gbc.gridx = 2;
        contentPane.add(trainingDataSetBrowseButton, gbc);

        gbc.gridy++;
        DialogUtils.addComponent(contentPane, gbc, windowSizeLabel, windowSize);
        DialogUtils.enableComponents(windowSizeLabel, windowSize, true);

        DialogUtils.fillPanel(contentPane, gbc);

        return contentPane;
    }

}
