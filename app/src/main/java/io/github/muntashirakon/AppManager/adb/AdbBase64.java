/*
 * Copyright (c) 2021 Muntashir Al-Islam
 * Copyright (c) 2016 Anton Tananaev
 * Copyright (c) 2013 Cameron Gutman
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.adb;

/**
 * This interface specifies the required functions for AdbCrypto to
 * perform Base64 encoding of its public key.
 */
public interface AdbBase64 {
    /**
     * This function must encoded the specified data as a base 64 string, without
     * appending any extra newlines or other characters.
     *
     * @param data Data to encode
     * @return String containing base 64 encoded data
     */
    String encodeToString(byte[] data);
}
