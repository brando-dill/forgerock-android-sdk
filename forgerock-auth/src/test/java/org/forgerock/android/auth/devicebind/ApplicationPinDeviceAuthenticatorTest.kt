/*
 * Copyright (c) 2022 - 2023 ForgeRock. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */
package org.forgerock.android.auth.devicebind

import android.content.Context
import android.os.OperationCanceledException
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.forgerock.android.auth.AppPinAuthenticator
import org.forgerock.android.auth.CryptoKey
import org.forgerock.android.auth.InitProvider
import org.forgerock.android.auth.devicebind.DeviceBindingErrorStatus.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ApplicationPinDeviceAuthenticatorTest {

    private val activity = mock<FragmentActivity>()
    private val cryptoKey = CryptoKey("bob")
    var context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        whenever(activity.applicationContext).thenReturn(ApplicationProvider.getApplicationContext())
        InitProvider.setCurrentActivity(activity)
    }

    @After
    fun tearDown() {
        InitProvider.setCurrentActivity(null);
    }

    @Test
    fun testIsSupported() {
        val authenticator = getApplicationPinDeviceAuthenticator()
        assertThat(authenticator.isSupported(context)).isTrue()
    }

    @Test
    fun testGenerateKeys() = runBlocking {
        val authenticator = getApplicationPinDeviceAuthenticator()
        val keyPair = authenticator.generateKeys(context)
        assertThat(keyPair).isNotNull
        assertThat(keyPair.keyAlias).isEqualTo(cryptoKey.keyAlias + "_PIN")
        //Test pin is cached for 1 sec
        assertThat(authenticator.size()).isGreaterThan(1000)
        assertThat(authenticator.pinRef.get()).isNotNull()
        delay(3000)
        assertThat(authenticator.pinRef.get()).isNull()
    }

    @Test
    fun testSuccessAuthenticated() = runBlocking {
        val authenticator = getApplicationPinDeviceAuthenticator()
        val keyPair = authenticator.generateKeys(context)
        val status = authenticator.authenticate(context)
        assertThat(status).isEqualTo(Success(keyPair.privateKey))
        assertThat(authenticator.pinRef.get()).isNull()
    }

    // Its hard to test this scenario without mocking, the keystore has to return null value to test this case
    @Test
    fun testUnRegisterWhenPrivateKeyIsNull(): Unit = runBlocking{
        val authenticator: ApplicationPinDeviceAuthenticator = getApplicationPinDeviceAuthenticator()
        val mockAppPinAuthenticator = mock<AppPinAuthenticator>()
        whenever(mockAppPinAuthenticator.getPrivateKey(any(), any())).thenReturn(null)
        authenticator.appPinAuthenticator = mockAppPinAuthenticator
        authenticator.pinRef.set("1234".toCharArray())
        assertThat(authenticator.authenticate(context)).isEqualTo(UnRegister())
    }

    @Test
    fun testUnRegister(): Unit = runBlocking {
        val authenticator = getApplicationPinDeviceAuthenticator()
        val status = authenticator.authenticate(context)
        assertThat(status).isEqualTo(UnRegister())
    }

    //Provide wrong pin
    @Test
    fun testUnAuthorize(): Unit = runBlocking {
        val authenticator = getApplicationPinDeviceAuthenticator()
        authenticator.generateKeys(context)
        //Using the same byte array buffer
        val authenticator2 = object : NoEncryptionApplicationPinDeviceAuthenticator() {
            override suspend fun requestForCredentials(fragmentActivity: FragmentActivity): CharArray {
                return ("invalidPin".toCharArray())
            }

            override fun getInputStream(context: Context): InputStream {
                return authenticator.getInputStream(context)
            }

            override fun getOutputStream(context: Context): OutputStream {
                return authenticator.getOutputStream(context)
            }

            override fun delete(context: Context) {
                authenticator.delete(context)
            }
        }
        authenticator2.setKey(cryptoKey)
        val status = authenticator2.authenticate(context)
        assertThat(status).isEqualTo(UnAuthorize())
    }

    @Test
    fun testAbort(): Unit = runBlocking {
        val authenticator = getApplicationPinDeviceAuthenticator()
        authenticator.generateKeys(context)
        //Using the same byte array buffer
        val authenticator2 = object : NoEncryptionApplicationPinDeviceAuthenticator() {
            override suspend fun requestForCredentials(fragmentActivity: FragmentActivity): CharArray {
                throw OperationCanceledException()
            }

            override fun getInputStream(context: Context): InputStream {
                return authenticator.getInputStream(context)
            }

            override fun getOutputStream(context: Context): OutputStream {
                return authenticator.getOutputStream(context)
            }

            override fun delete(context: Context) {
                authenticator.delete(context)
            }
        }
        authenticator2.setKey(cryptoKey)
        val status = authenticator2.authenticate(context)
        assertThat(status).isEqualTo(Abort())

    }

    private fun getApplicationPinDeviceAuthenticator(): NoEncryptionApplicationPinDeviceAuthenticator {
        val authenticator = NoEncryptionApplicationPinDeviceAuthenticator()
        authenticator.setKey(cryptoKey)
        return authenticator
    }

    open class NoEncryptionApplicationPinDeviceAuthenticator : ApplicationPinDeviceAuthenticator() {

        var byteArrayOutputStream = ByteArrayOutputStream(1024)

        override suspend fun requestForCredentials(fragmentActivity: FragmentActivity): CharArray {
            return "1234".toCharArray()
        }

        override fun getInputStream(context: Context): InputStream {
            return byteArrayOutputStream.toByteArray().inputStream();
        }

        override fun getOutputStream(context: Context): OutputStream {
            return byteArrayOutputStream
        }

        override fun getKeystoreType(): String {
            return "BKS"
        }

        override fun delete(context: Context) {
            byteArrayOutputStream = ByteArrayOutputStream(1024)
        }

        override fun deleteKeys(context: Context) {
            byteArrayOutputStream = ByteArrayOutputStream(1024)
        }

        fun size(): Int {
            return byteArrayOutputStream.toByteArray().size
        }
    }

}