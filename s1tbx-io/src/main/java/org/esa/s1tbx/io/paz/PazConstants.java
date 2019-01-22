/*
 * Copyright (C) 2019 Skywatch. https://www.skywatch.co
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
package org.esa.s1tbx.io.paz;

import java.io.File;

/**
 * Several constants used for reading TerraSarX products.
 */
class PazConstants {

    private final static String[] FORMAT_NAMES = new String[]{"PAZ"};
    private final static String[] FORMAT_FILE_EXTENSIONS = new String[]{"xml"};
    private final static String PLUGIN_DESCRIPTION = "Paz Products";
    final static String PRODUCT_TYPE_PREFIX = "";
    final static String[] HEADER_PREFIX = {"PAZ"};

    final static String PRODUCT_DESCRIPTION_PREFIX = "Paz product ";

    private final static String INDICATION_KEY = "XML";

    final static Class[] VALID_INPUT_TYPES = new Class[]{File.class, String.class};

    public static String getIndicationKey() {
        return INDICATION_KEY;
    }

    public static String getPluginDescription() {
        return PLUGIN_DESCRIPTION;
    }

    public static String[] getFormatNames() {
        return FORMAT_NAMES;
    }

    public static String[] getFormatFileExtensions() {
        return FORMAT_FILE_EXTENSIONS;
    }

}
