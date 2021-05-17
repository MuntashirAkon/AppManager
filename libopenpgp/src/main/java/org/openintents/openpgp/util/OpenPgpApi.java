// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-or-later

package org.openintents.openpgp.util;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import org.openintents.openpgp.IOpenPgpService2;
import org.openintents.openpgp.OpenPgpError;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

// Copyright 2014-2015 Dominik Schürmann
@SuppressWarnings("unused")
public class OpenPgpApi {

    public static final String TAG = "OpenPgp API";

    public static final String SERVICE_INTENT_2 = "org.openintents.openpgp.IOpenPgpService2";

    /**
     * see CHANGELOG.md
     */
    public static final int API_VERSION = 11;

    /*
     * General extras
     * --------------
     *
     * required extras:
     * int           EXTRA_API_VERSION           (always required)
     *
     * returned extras:
     * int           RESULT_CODE                 (RESULT_CODE_ERROR, RESULT_CODE_SUCCESS or RESULT_CODE_USER_INTERACTION_REQUIRED)
     * OpenPgpError  RESULT_ERROR                (if RESULT_CODE == RESULT_CODE_ERROR)
     * PendingIntent RESULT_INTENT               (if RESULT_CODE == RESULT_CODE_USER_INTERACTION_REQUIRED)
     */

    /**
     * This action performs no operation, but can be used to check if the App has permission
     * to access the API in general, returning a user interaction PendingIntent otherwise.
     * This can be used to trigger the permission dialog explicitly.
     * <p>
     * This action uses no extras.
     */
    public static final String ACTION_CHECK_PERMISSION = "org.openintents.openpgp.action.CHECK_PERMISSION";

    @Deprecated
    public static final String ACTION_SIGN = "org.openintents.openpgp.action.SIGN";

    /**
     * Sign text resulting in a cleartext signature
     * Some magic pre-processing of the text is done to convert it to a format usable for
     * cleartext signatures per RFC 4880 before the text is actually signed:
     * - end cleartext with newline
     * - remove whitespaces on line endings
     * <p>
     * required extras:
     * long          EXTRA_SIGN_KEY_ID           (key id of signing key)
     * <p>
     * optional extras:
     * char[]        EXTRA_PASSPHRASE            (key passphrase)
     */
    public static final String ACTION_CLEARTEXT_SIGN = "org.openintents.openpgp.action.CLEARTEXT_SIGN";

    /**
     * Sign text or binary data resulting in a detached signature.
     * No OutputStream necessary for ACTION_DETACHED_SIGN (No magic pre-processing like in ACTION_CLEARTEXT_SIGN)!
     * The detached signature is returned separately in RESULT_DETACHED_SIGNATURE.
     * <p>
     * required extras:
     * long          EXTRA_SIGN_KEY_ID           (key id of signing key)
     * <p>
     * optional extras:
     * boolean       EXTRA_REQUEST_ASCII_ARMOR   (request ascii armor for detached signature)
     * char[]        EXTRA_PASSPHRASE            (key passphrase)
     * <p>
     * returned extras:
     * byte[]        RESULT_DETACHED_SIGNATURE
     * String        RESULT_SIGNATURE_MICALG     (contains the name of the used signature algorithm as a string)
     */
    public static final String ACTION_DETACHED_SIGN = "org.openintents.openpgp.action.DETACHED_SIGN";

    /**
     * Encrypt
     * <p>
     * required extras:
     * String[]      EXTRA_USER_IDS              (=emails of recipients, if more than one key has a user_id, a PendingIntent is returned via RESULT_INTENT)
     * or
     * long[]        EXTRA_KEY_IDS
     * <p>
     * optional extras:
     * boolean       EXTRA_REQUEST_ASCII_ARMOR   (request ascii armor for output)
     * char[]        EXTRA_PASSPHRASE            (key passphrase)
     * String        EXTRA_ORIGINAL_FILENAME     (original filename to be encrypted as metadata)
     * boolean       EXTRA_ENABLE_COMPRESSION    (enable ZLIB compression, default ist true)
     */
    public static final String ACTION_ENCRYPT = "org.openintents.openpgp.action.ENCRYPT";

    /**
     * Sign and encrypt
     * <p>
     * required extras:
     * String[]      EXTRA_USER_IDS              (=emails of recipients, if more than one key has a user_id, a PendingIntent is returned via RESULT_INTENT)
     * or
     * long[]        EXTRA_KEY_IDS
     * <p>
     * optional extras:
     * long          EXTRA_SIGN_KEY_ID           (key id of signing key)
     * boolean       EXTRA_REQUEST_ASCII_ARMOR   (request ascii armor for output)
     * char[]        EXTRA_PASSPHRASE            (key passphrase)
     * String        EXTRA_ORIGINAL_FILENAME     (original filename to be encrypted as metadata)
     * boolean       EXTRA_ENABLE_COMPRESSION    (enable ZLIB compression, default ist true)
     */
    public static final String ACTION_SIGN_AND_ENCRYPT = "org.openintents.openpgp.action.SIGN_AND_ENCRYPT";

    public static final String ACTION_QUERY_AUTOCRYPT_STATUS = "org.openintents.openpgp.action.QUERY_AUTOCRYPT_STATUS";

    /**
     * Decrypts and verifies given input stream. This methods handles encrypted-only, signed-and-encrypted,
     * and also signed-only input.
     * OutputStream is optional, e.g., for verifying detached signatures!
     * <p>
     * If OpenPgpSignatureResult.getResult() == OpenPgpSignatureResult.RESULT_KEY_MISSING
     * in addition a PendingIntent is returned via RESULT_INTENT to download missing keys.
     * On all other status, in addition a PendingIntent is returned via RESULT_INTENT to open
     * the key view in OpenKeychain.
     * <p>
     * optional extras:
     * byte[]        EXTRA_DETACHED_SIGNATURE    (detached signature)
     * <p>
     * returned extras:
     * OpenPgpSignatureResult   RESULT_SIGNATURE
     * OpenPgpDecryptionResult  RESULT_DECRYPTION
     * OpenPgpDecryptMetadata   RESULT_METADATA
     * String                   RESULT_CHARSET   (charset which was specified in the headers of ascii armored input, if any)
     */
    public static final String ACTION_DECRYPT_VERIFY = "org.openintents.openpgp.action.DECRYPT_VERIFY";

    /**
     * Decrypts the header of an encrypted file to retrieve metadata such as original filename.
     * <p>
     * This does not decrypt the actual content of the file.
     * <p>
     * returned extras:
     * OpenPgpDecryptMetadata   RESULT_METADATA
     * String                   RESULT_CHARSET   (charset which was specified in the headers of ascii armored input, if any)
     */
    public static final String ACTION_DECRYPT_METADATA = "org.openintents.openpgp.action.DECRYPT_METADATA";

    /**
     * Select key id for signing
     * <p>
     * optional extras:
     * String      EXTRA_USER_ID
     * <p>
     * returned extras:
     * long        EXTRA_SIGN_KEY_ID
     */
    public static final String ACTION_GET_SIGN_KEY_ID = "org.openintents.openpgp.action.GET_SIGN_KEY_ID";
    public static final String ACTION_GET_SIGN_KEY_ID_LEGACY = "org.openintents.openpgp.action.GET_SIGN_KEY_ID_LEGACY";

    /**
     * Get key ids based on given user ids (=emails)
     * <p>
     * required extras:
     * String[]      EXTRA_USER_IDS
     * <p>
     * returned extras:
     * long[]        RESULT_KEY_IDS
     */
    public static final String ACTION_GET_KEY_IDS = "org.openintents.openpgp.action.GET_KEY_IDS";

    /**
     * This action returns RESULT_CODE_SUCCESS if the OpenPGP Provider already has the key
     * corresponding to the given key id in its database.
     * <p>
     * It returns RESULT_CODE_USER_INTERACTION_REQUIRED if the Provider does not have the key.
     * The PendingIntent from RESULT_INTENT can be used to retrieve those from a keyserver.
     * <p>
     * If an Output stream has been defined the whole public key is returned.
     * required extras:
     * long        EXTRA_KEY_ID
     * <p>
     * optional extras:
     * String      EXTRA_REQUEST_ASCII_ARMOR (request that the returned key is encoded in ASCII Armor)
     */
    public static final String ACTION_GET_KEY = "org.openintents.openpgp.action.GET_KEY";

    /**
     * Backup all keys given by EXTRA_KEY_IDS and if requested their secret parts.
     * The encrypted backup will be written to the OutputStream.
     * The client app has no access to the backup code used to encrypt the backup!
     * This operation always requires user interaction with RESULT_CODE_USER_INTERACTION_REQUIRED!
     * <p>
     * required extras:
     * long[]      EXTRA_KEY_IDS       (keys that should be included in the backup)
     * boolean     EXTRA_BACKUP_SECRET (also backup secret keys)
     */
    public static final String ACTION_BACKUP = "org.openintents.openpgp.action.BACKUP";

    public static final String ACTION_UPDATE_AUTOCRYPT_PEER = "org.openintents.openpgp.action.UPDATE_AUTOCRYPT_PEER";

    /* Intent extras */
    public static final String EXTRA_API_VERSION = "api_version";

    // ACTION_DETACHED_SIGN, ENCRYPT, SIGN_AND_ENCRYPT, DECRYPT_VERIFY
    // request ASCII Armor for output
    // OpenPGP Radix-64, 33 percent overhead compared to binary, see http://tools.ietf.org/html/rfc4880#page-53)
    public static final String EXTRA_REQUEST_ASCII_ARMOR = "ascii_armor";

    // ACTION_DETACHED_SIGN
    public static final String RESULT_DETACHED_SIGNATURE = "detached_signature";
    public static final String RESULT_SIGNATURE_MICALG = "signature_micalg";

    // ENCRYPT, SIGN_AND_ENCRYPT, QUERY_AUTOCRYPT_STATUS
    public static final String EXTRA_USER_IDS = "user_ids";
    public static final String EXTRA_KEY_IDS = "key_ids";
    public static final String EXTRA_KEY_IDS_SELECTED = "key_ids_selected";
    public static final String EXTRA_SIGN_KEY_ID = "sign_key_id";

    public static final String RESULT_KEYS_CONFIRMED = "keys_confirmed";
    public static final String RESULT_AUTOCRYPT_STATUS = "autocrypt_status";
    public static final int AUTOCRYPT_STATUS_UNAVAILABLE = 0;
    public static final int AUTOCRYPT_STATUS_DISCOURAGE = 1;
    public static final int AUTOCRYPT_STATUS_AVAILABLE = 2;
    public static final int AUTOCRYPT_STATUS_MUTUAL = 3;

    // optional extras:
    public static final String EXTRA_PASSPHRASE = "passphrase";
    public static final String EXTRA_ORIGINAL_FILENAME = "original_filename";
    public static final String EXTRA_ENABLE_COMPRESSION = "enable_compression";
    public static final String EXTRA_OPPORTUNISTIC_ENCRYPTION = "opportunistic";
    public static final String EXTRA_CUSTOM_HEADERS = "custom_headers";

    // GET_SIGN_KEY_ID
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_PRESELECT_KEY_ID = "preselect_key_id";
    public static final String EXTRA_SHOW_AUTOCRYPT_HINT = "show_autocrypt_hint";

    public static final String RESULT_SIGN_KEY_ID = "sign_key_id";
    public static final String RESULT_PRIMARY_USER_ID = "primary_user_id";
    public static final String RESULT_KEY_CREATION_TIME = "key_creation_time";

    // GET_KEY
    public static final String EXTRA_KEY_ID = "key_id";
    public static final String EXTRA_MINIMIZE = "minimize";
    public static final String EXTRA_MINIMIZE_USER_ID = "minimize_user_id";
    public static final String RESULT_KEY_IDS = "key_ids";

    // BACKUP
    public static final String EXTRA_BACKUP_SECRET = "backup_secret";

    public static final String ACTION_AUTOCRYPT_KEY_TRANSFER = "autocrypt_key_transfer";

    /* Service Intent returns */
    public static final String RESULT_CODE = "result_code";

    // get actual error object from RESULT_ERROR
    public static final int RESULT_CODE_ERROR = 0;
    // success!
    public static final int RESULT_CODE_SUCCESS = 1;
    // get PendingIntent from RESULT_INTENT, start PendingIntent with startIntentSenderForResult,
    // and execute service method again in onActivityResult
    public static final int RESULT_CODE_USER_INTERACTION_REQUIRED = 2;

    public static final String RESULT_ERROR = "error";
    public static final String RESULT_INTENT = "intent";

    // DECRYPT_VERIFY
    public static final String EXTRA_DETACHED_SIGNATURE = "detached_signature";
    public static final String EXTRA_PROGRESS_MESSENGER = "progress_messenger";
    public static final String EXTRA_DATA_LENGTH = "data_length";
    public static final String EXTRA_DECRYPTION_RESULT = "decryption_result";
    public static final String EXTRA_SENDER_ADDRESS = "sender_address";
    public static final String EXTRA_SUPPORT_OVERRIDE_CRYPTO_WARNING = "support_override_crpto_warning";
    public static final String EXTRA_AUTOCRYPT_PEER_ID = "autocrypt_peer_id";
    public static final String EXTRA_AUTOCRYPT_PEER_UPDATE = "autocrypt_peer_update";
    public static final String EXTRA_AUTOCRYPT_PEER_GOSSIP_UPDATES = "autocrypt_peer_gossip_updates";
    public static final String RESULT_SIGNATURE = "signature";
    public static final String RESULT_DECRYPTION = "decryption";
    public static final String RESULT_METADATA = "metadata";
    public static final String RESULT_INSECURE_DETAIL_INTENT = "insecure_detail_intent";
    public static final String RESULT_OVERRIDE_CRYPTO_WARNING = "override_crypto_warning";
    // This will be the charset which was specified in the headers of ascii armored input, if any
    public static final String RESULT_CHARSET = "charset";

    // INTERNAL, must not be used
    public static final String EXTRA_CALL_UUID1 = "call_uuid1";
    public static final String EXTRA_CALL_UUID2 = "call_uuid2";

    IOpenPgpService2 mService;
    Context mContext;
    final AtomicInteger mPipeIdGen = new AtomicInteger();

    public OpenPgpApi(Context context, IOpenPgpService2 service) {
        this.mContext = context;
        this.mService = service;
    }

    public interface IOpenPgpCallback {
        void onReturn(final Intent result);
    }

    public void executeApiAsync(@NonNull Executor executor, Intent data, InputStream is,
                                OutputStream os, IOpenPgpCallback callback) {
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            Intent result = executeApi(data, is, os);
            handler.post(() -> callback.onReturn(result));
        });
    }

    public Intent executeApi(Intent data, InputStream is, OutputStream os) {
        ParcelFileDescriptor input = null;
        try {
            if (is != null) {
                input = ParcelFileDescriptorUtil.pipeFrom(is);
            }

            return executeApi(data, input, os);
        } catch (Exception e) {
            Log.e(OpenPgpApi.TAG, "Exception in executeApi call", e);
            Intent result = new Intent();
            result.putExtra(RESULT_CODE, RESULT_CODE_ERROR);
            result.putExtra(RESULT_ERROR, new OpenPgpError(OpenPgpError.CLIENT_SIDE_ERROR, e.getMessage()));
            return result;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    Log.e(OpenPgpApi.TAG, "IOException when closing ParcelFileDescriptor!", e);
                }
            }
        }
    }

    /**
     * InputStream and OutputStreams are always closed after operating on them!
     */
    @NonNull
    private Intent executeApi(Intent data, ParcelFileDescriptor input, OutputStream os) {
        ParcelFileDescriptor output = null;
        try {
            // always send version from client
            data.putExtra(EXTRA_API_VERSION, OpenPgpApi.API_VERSION);

            Intent result;

            Thread pumpThread = null;
            int outputPipeId = 0;

            if (os != null) {
                outputPipeId = mPipeIdGen.incrementAndGet();
                output = mService.createOutputPipe(outputPipeId);
                pumpThread = ParcelFileDescriptorUtil.pipeTo(os, output);
            }

            // blocks until result is ready
            result = mService.execute(data, input, outputPipeId);

            // set class loader to current context to allow unparcelling
            // of OpenPgpError and OpenPgpSignatureResult
            // http://stackoverflow.com/a/3806769
            result.setExtrasClassLoader(mContext.getClassLoader());

            //wait for ALL data being pumped from remote side
            if (pumpThread != null) {
                pumpThread.join();
            }

            return result;
        } catch (Exception e) {
            Log.e(OpenPgpApi.TAG, "Exception in executeApi call", e);
            Intent result = new Intent();
            result.putExtra(RESULT_CODE, RESULT_CODE_ERROR);
            result.putExtra(RESULT_ERROR,
                    new OpenPgpError(OpenPgpError.CLIENT_SIDE_ERROR, e.getMessage()));
            return result;
        } finally {
            // close() is required to halt the TransferThread
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    Log.e(OpenPgpApi.TAG, "IOException when closing ParcelFileDescriptor!", e);
                }
            }
        }
    }

}
