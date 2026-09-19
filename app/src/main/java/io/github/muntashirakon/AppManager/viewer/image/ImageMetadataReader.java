// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.image;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;

/**
 * Reads the complete set of EXIF attributes exposed by AndroidX ExifInterface.
 */
final class ImageMetadataReader {
    private static final int MAX_VALUE_LENGTH = 16 * 1024;
    private static final int MAX_OUTPUT_LENGTH = 128 * 1024;

    private static final HashSet<String> tagsToIgnore = new HashSet<String>() {{
        add(ExifInterface.TAG_PIXEL_X_DIMENSION);
        add(ExifInterface.TAG_PIXEL_Y_DIMENSION);
        add(ExifInterface.TAG_APERTURE_VALUE); // APEX of F_NUMBER
        add(ExifInterface.TAG_F_NUMBER);
        add(ExifInterface.TAG_MAX_APERTURE_VALUE);
        add(ExifInterface.TAG_FOCAL_LENGTH);
        add(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM);
        add(ExifInterface.TAG_BRIGHTNESS_VALUE);
        add(ExifInterface.TAG_METERING_MODE);
        add(ExifInterface.TAG_LIGHT_SOURCE);
        add(ExifInterface.TAG_COLOR_SPACE);
        add(ExifInterface.TAG_CONTRAST);
        add(ExifInterface.TAG_EXPOSURE_TIME);
        add(ExifInterface.TAG_SHUTTER_SPEED_VALUE); // APEX of EXPOSURE_TIME (-log(t))
        add(ExifInterface.TAG_EXPOSURE_MODE);
        add(ExifInterface.TAG_EXPOSURE_PROGRAM);
        add(ExifInterface.TAG_ORIENTATION);
        add(ExifInterface.TAG_FLASH);
        add(ExifInterface.TAG_GAIN_CONTROL);
        add(ExifInterface.TAG_GPS_LATITUDE);
        add(ExifInterface.TAG_GPS_LATITUDE_REF);
        add(ExifInterface.TAG_GPS_LONGITUDE);
        add(ExifInterface.TAG_GPS_LONGITUDE_REF);
        add(ExifInterface.TAG_GPS_ALTITUDE);
        add(ExifInterface.TAG_GPS_ALTITUDE_REF);
        add(ExifInterface.TAG_SCENE_CAPTURE_TYPE);
        add(ExifInterface.TAG_SENSING_METHOD);
        add(ExifInterface.TAG_SHARPNESS);
        add(ExifInterface.TAG_SUBJECT_DISTANCE);
        add(ExifInterface.TAG_SUBJECT_DISTANCE_RANGE);
        add(ExifInterface.TAG_WHITE_BALANCE);
    }};

    private ImageMetadataReader() {
    }

    @NonNull
    static CharSequence read(@NonNull Context context, @NonNull Path path) throws IOException {
        SpannableStringBuilder result = new SpannableStringBuilder();
        try (InputStream input = path.openInputStream()) {
            ExifInterface exif = new ExifInterface(input);
            long xDimen = exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, -1);
            long yDimen = exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, -1);
            if (xDimen != -1 && yDimen != -1) {
                append(context, result, "Dimension", xDimen + "×" + yDimen);
            }
            append(context, result, "Aperture", getAperture(exif));
            append(context, result, "Max aperture", getMaxAperture(exif));
            append(context, result, "Focal length", getFocalLength(exif));
            append(context, result, "Brightness", getBrightness(exif));
            append(context, result, "Metering mode", getMeteringMode(exif));
            append(context, result, "Light source", getLightSource(exif));
            append(context, result, "Color space", getColorSpace(exif));
            append(context, result, "Contrast", getContrast(exif));
            double exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, Double.NaN);
            if (!Double.isNaN(exposureTime)) {
                append(context, result, "Exposure", String.format(Locale.getDefault(), "%.2f s", exposureTime));
            }
            String exposureMode = getExposureMode(exif);
            append(context, result, "Exposure mode", exposureMode);
            String exposureProgram = getExposureProgram(exif);
            append(context, result, "Exposure program", exposureProgram);
            append(context, result, "Flash", getFlash(exif));
            append(context, result, "Gain control", getGainControl(exif));
            double[] latLong = exif.getLatLong();
            if (latLong != null) {
                append(context, result, "GPS latitude", String.format(Locale.getDefault(), "%.3f", latLong[0]));
                append(context, result, "GPS longitude", String.format(Locale.getDefault(), "%.3f", latLong[1]));
            }
            double altitude = exif.getAltitude(Double.NaN);
            if (!Double.isNaN(altitude)) {
                append(context, result, "GPS altitude", String.format(Locale.getDefault(), "%.2f m", altitude));
            }
            append(context, result, "Orientation", getOrientation(exif));
            append(context, result, "Scene capture type", getSceneCaptureType(exif));
            append(context, result, "Sensing method", getSensingMethod(exif));
            append(context, result, "Sharpness", getSharpness(exif));
            append(context, result, "Subject distance", getSubjectDistance(exif));
            append(context, result, "Subject distance range", getSubjectDistanceRange(exif));
            append(context, result, "White balance", getWhiteBalance(exif));

            result.append("\n");
            List<String> tags = getExifTags();
            for (String tag : tags) {
                if (tagsToIgnore.contains(tag)) {
                    continue;
                }
                String value;
                try {
                    value = exif.getAttribute(tag);
                } catch (Throwable ignored) {
                    continue;
                }
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                append(context, result, toLabel(tag), limit(value));
                if (result.length() >= MAX_OUTPUT_LENGTH) {
                    result.append("\n")
                            .append(UIUtils.getSecondaryText(context, "[output truncated]"));
                    break;
                }
            }
        }
        return result;
    }

    @NonNull
    private static List<String> getExifTags() {
        List<String> tags = new ArrayList<>();
        for (Field field : ExifInterface.class.getFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || field.getType() != String.class
                    || !field.getName().startsWith("TAG_")) {
                continue;
            }
            try {
                Object value = field.get(null);
                if (value instanceof String) {
                    tags.add((String) value);
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        Collections.sort(tags);
        return tags;
    }

    @NonNull
    private static String toLabel(@NonNull String tag) {
        String label = tag.startsWith("TAG_") ? tag.substring(4) : tag;
        StringBuilder result = new StringBuilder(label.length() + 8);
        for (int i = 0; i < label.length(); ++i) {
            char c = label.charAt(i);
            if (i > 0 && Character.isUpperCase(c)
                    && (Character.isLowerCase(label.charAt(i - 1))
                    || (i + 1 < label.length() && Character.isLowerCase(label.charAt(i + 1))))) {
                result.append(' ');
            }
            result.append(c);
        }
        return result.toString().replace('_', ' ');
    }

    @NonNull
    private static String limit(@NonNull String value) {
        return value.length() <= MAX_VALUE_LENGTH
                ? value : value.substring(0, MAX_VALUE_LENGTH) + "…";
    }

    @Nullable
    private static String getAperture(@NonNull ExifInterface exif) {
        double av = exif.getAttributeDouble(ExifInterface.TAG_APERTURE_VALUE, Double.NaN);
        double f;
        if (!Double.isNaN(av)) {
            // f = 2 ^ (Av/2)
            f = Math.pow(2, av / 2);
        } else {
            // Fall back to F_NUMBER
            f = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, Double.NaN);
        }
        if (Double.isNaN(f)) {
            // Not a valid number
            return null;
        }
        return String.format(Locale.getDefault(), "f/%.1f", f);
    }

    @Nullable
    private static String getMaxAperture(@NonNull ExifInterface exif) {
        double av = exif.getAttributeDouble(ExifInterface.TAG_MAX_APERTURE_VALUE, Double.NaN);
        if (!Double.isNaN(av)) {
            // f = 2 ^ (Av/2)
            double f = Math.pow(2, av / 2);
            return String.format(Locale.getDefault(), "f/%.1f", f);
        }
        return null;
    }

    @Nullable
    private static String getBrightness(@NonNull ExifInterface exif) {
        double bv = exif.getAttributeDouble(ExifInterface.TAG_BRIGHTNESS_VALUE, Double.NaN);
        if (Double.isNaN(bv)) {
            return null;
        }
        // l = 4 * 2 ^ bv
        // Pentax, Minolta, Kenko uses 4.48, but we considered the regular ones
        double l = 4 * Math.pow(2, bv);
        return String.format(Locale.getDefault(), "%.2f cd/m²", l);
    }

    @Nullable
    private static String getContrast(@NonNull ExifInterface exif) {
        int contrast = exif.getAttributeInt(ExifInterface.TAG_CONTRAST, -1);
        switch (contrast) {
            case ExifInterface.CONTRAST_NORMAL:
                return "Normal";
            case ExifInterface.CONTRAST_SOFT:
                return "Soft";
            case ExifInterface.CONTRAST_HARD:
                return "Hard";
            default:
                return null;
        }
    }

    @Nullable
    private static String getOrientation(@NonNull ExifInterface exif) {
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED);
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return "Normal";
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                return "Flipped horizontally";
            case ExifInterface.ORIENTATION_ROTATE_180:
                return "Rotated 180°";
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                return "Flipped vertically";
            case ExifInterface.ORIENTATION_TRANSPOSE:
                return "Transposed";
            case ExifInterface.ORIENTATION_ROTATE_90:
                return "Rotated 90° clockwise";
            case ExifInterface.ORIENTATION_TRANSVERSE:
                return "Transverse";
            case ExifInterface.ORIENTATION_ROTATE_270:
                return "Rotated 270° clockwise";
            default:
                return null;
        }
    }

    @Nullable
    private static String getSharpness(@NonNull ExifInterface exif) {
        int sharpness = exif.getAttributeInt(ExifInterface.TAG_SHARPNESS, -1);
        switch (sharpness) {
            case ExifInterface.SHARPNESS_NORMAL:
                return "Normal";
            case ExifInterface.SHARPNESS_SOFT:
                return "Soft";
            case ExifInterface.SHARPNESS_HARD:
                return "Hard";
            default:
                return null;
        }
    }

    @Nullable
    private static String getGainControl(@NonNull ExifInterface exif) {
        int gain = exif.getAttributeInt(ExifInterface.TAG_GAIN_CONTROL, -1);
        switch (gain) {
            case ExifInterface.GAIN_CONTROL_NONE:
                return "None";
            case ExifInterface.GAIN_CONTROL_LOW_GAIN_UP:
                return "Low Gain Up";
            case ExifInterface.GAIN_CONTROL_HIGH_GAIN_UP:
                return "High Gain Up";
            case ExifInterface.GAIN_CONTROL_LOW_GAIN_DOWN:
                return "Low Gain Down";
            case ExifInterface.GAIN_CONTROL_HIGH_GAIN_DOWN:
                return "High Gain Down";
            default:
                return null;
        }
    }


    @Nullable
    private static String getSceneCaptureType(@NonNull ExifInterface exif) {
        int scene = exif.getAttributeInt(ExifInterface.TAG_SCENE_CAPTURE_TYPE, -1);
        switch (scene) {
            case ExifInterface.SCENE_CAPTURE_TYPE_STANDARD:
                return "Standard";
            case ExifInterface.SCENE_CAPTURE_TYPE_LANDSCAPE:
                return "Landscape";
            case ExifInterface.SCENE_CAPTURE_TYPE_PORTRAIT:
                return "Portrait";
            case ExifInterface.SCENE_CAPTURE_TYPE_NIGHT:
                return "Night scene";
            default:
                return null;
        }
    }

    @Nullable
    private static String getSensingMethod(@NonNull ExifInterface exif) {
        int sensor = exif.getAttributeInt(ExifInterface.TAG_SENSING_METHOD, -1);
        switch (sensor) {
            case ExifInterface.SENSOR_TYPE_NOT_DEFINED:
                return "Not defined";
            case ExifInterface.SENSOR_TYPE_ONE_CHIP:
                return "One-chip color area sensor";
            case ExifInterface.SENSOR_TYPE_TWO_CHIP:
                return "Two-chip color area sensor";
            case ExifInterface.SENSOR_TYPE_THREE_CHIP:
                return "Three-chip color area sensor";
            case ExifInterface.SENSOR_TYPE_COLOR_SEQUENTIAL:
                return "Color sequential area sensor";
            case ExifInterface.SENSOR_TYPE_TRILINEAR:
                return "Trilinear sensor";
            case ExifInterface.SENSOR_TYPE_COLOR_SEQUENTIAL_LINEAR:
                return "Color sequential linear sensor";
            default:
                return null;
        }
    }

    @Nullable
    private static String getColorSpace(@NonNull ExifInterface exif) {
        int color = exif.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, -1);
        switch (color) {
            case ExifInterface.COLOR_SPACE_S_RGB:
                return "sRGB";
            case ExifInterface.COLOR_SPACE_UNCALIBRATED:
                return "Uncalibrated";
            default:
                return null;
        }
    }

    @Nullable
    private static String getExposureMode(@NonNull ExifInterface exif) {
        int mode = exif.getAttributeInt(ExifInterface.TAG_EXPOSURE_MODE, -1);
        switch (mode) {
            case ExifInterface.EXPOSURE_MODE_AUTO:
                return "Auto";
            case ExifInterface.EXPOSURE_MODE_MANUAL:
                return "Manual";
            case ExifInterface.EXPOSURE_MODE_AUTO_BRACKET:
                return "Auto bracket";
            default:
                return null;
        }
    }

    @Nullable
    private static String getExposureProgram(@NonNull ExifInterface exif) {
        int program = exif.getAttributeInt(ExifInterface.TAG_EXPOSURE_PROGRAM, -1);
        switch (program) {
            case ExifInterface.EXPOSURE_PROGRAM_NOT_DEFINED:
                return "Not defined";
            case ExifInterface.EXPOSURE_PROGRAM_MANUAL:
                return "Manual";
            case ExifInterface.EXPOSURE_PROGRAM_NORMAL:
                return "Normal";
            case ExifInterface.EXPOSURE_PROGRAM_APERTURE_PRIORITY:
                return "Aperture priority";
            case ExifInterface.EXPOSURE_PROGRAM_SHUTTER_PRIORITY:
                return "Shutter priority";
            case ExifInterface.EXPOSURE_PROGRAM_CREATIVE:
                return "Creative program (biased toward depth of field)";
            case ExifInterface.EXPOSURE_PROGRAM_ACTION:
                return "Action program (biased toward fast shutter speed)";
            case ExifInterface.EXPOSURE_PROGRAM_PORTRAIT_MODE:
                return "Portrait mode (closeup photo with the background out of focus)";
            case ExifInterface.EXPOSURE_PROGRAM_LANDSCAPE_MODE:
                return "Landscape mode (landscape photo with the background in focus)";
            default:
                return null;
        }
    }

    @Nullable
    private static String getFocalLength(@NonNull ExifInterface exif) {
        double focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, Double.NaN);
        double focalLengthIn35mm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, Double.NaN);
        if (Double.isNaN(focalLengthIn35mm)) {
            if (Double.isNaN(focalLength)) {
                // Not available
                return null;
            }
            // Switch values
            focalLengthIn35mm = focalLength;
            focalLength = Double.NaN;
        }
        String actual = Double.isNaN(focalLength) ? ""
                : String.format(Locale.getDefault(), " (actual: %.2f mm)", focalLength);
        return String.format(Locale.getDefault(), "%.2f mm%s", focalLengthIn35mm, actual);
    }

    @Nullable
    private static String getMeteringMode(@NonNull ExifInterface exif) {
        int mode = exif.getAttributeInt(ExifInterface.TAG_METERING_MODE, -1);
        switch (mode) {
            case ExifInterface.METERING_MODE_UNKNOWN:
                return "Unknown";
            case ExifInterface.METERING_MODE_AVERAGE:
                return "Average";
            case ExifInterface.METERING_MODE_CENTER_WEIGHT_AVERAGE:
                return "CenterWeightedAverage";
            case ExifInterface.METERING_MODE_SPOT:
                return "Spot";
            case ExifInterface.METERING_MODE_MULTI_SPOT:
                return "MultiSpot";
            case ExifInterface.METERING_MODE_PATTERN:
                return "Pattern";
            case ExifInterface.METERING_MODE_PARTIAL:
                return "Partial";
            case ExifInterface.METERING_MODE_OTHER:
                return "Other";
            default:
                return null;
        }
    }

    @Nullable
    private static String getLightSource(@NonNull ExifInterface exif) {
        int lightSource = exif.getAttributeInt(ExifInterface.TAG_LIGHT_SOURCE, -1);
        switch (lightSource) {
            case ExifInterface.LIGHT_SOURCE_UNKNOWN:
                return "Unknown";
            case ExifInterface.LIGHT_SOURCE_DAYLIGHT:
                return "Daylight";
            case ExifInterface.LIGHT_SOURCE_FLUORESCENT:
                return "Fluorescent";
            case ExifInterface.LIGHT_SOURCE_TUNGSTEN:
                return "Tungsten (incandescent light)";
            case ExifInterface.LIGHT_SOURCE_FLASH:
                return "Flash";
            case ExifInterface.LIGHT_SOURCE_FINE_WEATHER:
                return "Fine weather";
            case ExifInterface.LIGHT_SOURCE_CLOUDY_WEATHER:
                return "Cloudy weather";
            case ExifInterface.LIGHT_SOURCE_SHADE:
                return "Shade";
            case ExifInterface.LIGHT_SOURCE_DAYLIGHT_FLUORESCENT:
                return "Daylight fluorescent (D 5700 - 7100K)";
            case ExifInterface.LIGHT_SOURCE_DAY_WHITE_FLUORESCENT:
                return "Day white fluorescent (N 4600 - 5500K)";
            case ExifInterface.LIGHT_SOURCE_COOL_WHITE_FLUORESCENT:
                return "Cool white fluorescent (W 3800 - 4500K)";
            case ExifInterface.LIGHT_SOURCE_WHITE_FLUORESCENT:
                return "White fluorescent (WW 3250 - 3800K)";
            case ExifInterface.LIGHT_SOURCE_WARM_WHITE_FLUORESCENT:
                return "Warm white fluorescent (L 2600 - 3250K)";
            case ExifInterface.LIGHT_SOURCE_STANDARD_LIGHT_A:
                return "Standard light A";
            case ExifInterface.LIGHT_SOURCE_STANDARD_LIGHT_B:
                return "Standard light B";
            case ExifInterface.LIGHT_SOURCE_STANDARD_LIGHT_C:
                return "Standard light C";
            case ExifInterface.LIGHT_SOURCE_D55:
                return "D55";
            case ExifInterface.LIGHT_SOURCE_D65:
                return "D65";
            case ExifInterface.LIGHT_SOURCE_D75:
                return "D75";
            case ExifInterface.LIGHT_SOURCE_D50:
                return "D50";
            case ExifInterface.LIGHT_SOURCE_ISO_STUDIO_TUNGSTEN:
                return "ISO studio tungsten";
            case ExifInterface.LIGHT_SOURCE_OTHER:
                return "Other light source";
            default:
                return null;
        }
    }

    @Nullable
    private static String getWhiteBalance(@NonNull ExifInterface exif) {
        int whiteBalance = exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1);
        switch (whiteBalance) {
            case ExifInterface.WHITE_BALANCE_AUTO:
                return "Auto";
            case ExifInterface.WHITE_BALANCE_MANUAL:
                return "Manual";
            default:
                return null;
        }
    }

    @Nullable
    private static String getSubjectDistance(@NonNull ExifInterface exif) {
        double subject = exif.getAttributeDouble(ExifInterface.TAG_SUBJECT_DISTANCE, Double.NaN);
        if (Double.isNaN(subject)) {
            return null;
        }
        if (subject == 0xFFFFFFFF) {
            return "Infinity";
        }
        if (subject == 0) {
            return "Unknown";
        }
        return String.format(Locale.getDefault(), "%.2f m", subject);
    }

    @Nullable
    private static String getSubjectDistanceRange(@NonNull ExifInterface exif) {
        int subject = exif.getAttributeInt(ExifInterface.TAG_SUBJECT_DISTANCE_RANGE, -1);
        switch (subject) {
            case ExifInterface.SUBJECT_DISTANCE_RANGE_UNKNOWN:
                return "Unknown";
            case ExifInterface.SUBJECT_DISTANCE_RANGE_MACRO:
                return "Macro";
            case ExifInterface.SUBJECT_DISTANCE_RANGE_CLOSE_VIEW:
                return "Close View";
            case ExifInterface.SUBJECT_DISTANCE_RANGE_DISTANT_VIEW:
                return "Distant View";
            default:
                return null;
        }
    }

    @Nullable
    private static String getFlash(@NonNull ExifInterface exif) {
        int flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1);
        switch (flash) {
            case 0x0000:
                return "Flash did not fire";
            case 0x0001:
                return "Flash fired";
            case 0x0005:
                return "Strobe return light not detected";
            case 0x0007:
                return "Strobe return light detected";
            case 0x0009:
                return "Flash fired, compulsory flash mode";
            case 0x000D:
                return "Flash fired, compulsory flash mode, return light not detected";
            case 0x000F:
                return "Flash fired, compulsory flash mode, return light detected";
            case 0x0010:
                return "Flash did not fire, compulsory flash mode";
            case 0x0018:
                return "Flash did not fire, auto mode";
            case 0x0019:
                return "Flash fired, auto mode";
            case 0x001D:
                return "Flash fired, auto mode, return light not detected";
            case 0x001F:
                return "Flash fired, auto mode, return light detected";
            case 0x0020:
                return "No flash function";
            case 0x0041:
                return "Flash fired, red-eye reduction mode";
            case 0x0045:
                return "Flash fired, red-eye reduction mode, return light not detected";
            case 0x0047:
                return "Flash fired, red-eye reduction mode, return light detected";
            case 0x0049:
                return "Flash fired, compulsory flash mode, red-eye reduction mode";
            case 0x004D:
                return "Flash fired, compulsory flash mode, red-eye reduction mode, return light not detected";
            case 0x004F:
                return "Flash fired, compulsory flash mode, red-eye reduction mode, return light detected";
            case 0x0059:
                return "Flash fired, auto mode, red-eye reduction mode";
            case 0x005D:
                return "Flash fired, auto mode, return light not detected, red-eye reduction mode";
            case 0x005F:
                return "Flash fired, auto mode, return light detected, red-eye reduction mode";
            default:
                return null;
        }
    }

    private static void append(@NonNull Context context, @NonNull SpannableStringBuilder result,
                               @NonNull String label, @Nullable String value) {
        if (value == null || value.isEmpty() || result.length() >= MAX_OUTPUT_LENGTH) {
            return;
        }
        if (result.length() > 0) {
            result.append('\n');
        }
        result.append(UIUtils.getStyledKeyValue(context, label, value));
    }
}
