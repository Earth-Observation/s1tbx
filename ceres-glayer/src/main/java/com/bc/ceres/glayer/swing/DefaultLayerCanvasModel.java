package com.bc.ceres.glayer.swing;

import com.bc.ceres.glayer.Layer;
import com.bc.ceres.grender.Viewport;

public class DefaultLayerCanvasModel implements LayerCanvasModel {
    private final Layer layer;
    private final Viewport viewport;

    public DefaultLayerCanvasModel(Layer layer, Viewport viewport) {
        this.layer = layer;
        this.viewport = viewport;
    }

    public Layer getLayer() {
        return layer;
    }

    public Viewport getViewport() {
        return viewport;
    }

    public void addChangeListener(ChangeListener listener) {
        getLayer().addListener(listener);
        getViewport().addListener(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        getLayer().removeListener(listener);
        getViewport().removeListener(listener);
    }

}
