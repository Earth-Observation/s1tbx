package com.bc.ceres.swing.figure.support;

import java.awt.Paint;
import java.awt.Stroke;

public interface FigureStyle extends Cloneable {
    Stroke getDrawStroke();

    Paint getDrawPaint();

    Paint getFillPaint();

    @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
    FigureStyle clone();
}
