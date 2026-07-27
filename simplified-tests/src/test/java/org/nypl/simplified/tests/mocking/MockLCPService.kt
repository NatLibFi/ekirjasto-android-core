package org.nypl.simplified.tests.mocking

import org.readium.r2.lcp.LcpAuthenticating
import org.readium.r2.lcp.LcpError
import org.readium.r2.lcp.LcpLicense
import org.readium.r2.lcp.LcpService
import org.readium.r2.lcp.license.model.LicenseDocument
import org.readium.r2.shared.publication.protection.ContentProtection
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.Asset
import org.readium.r2.shared.util.asset.ContainerAsset
import java.io.File

/**
 * A mock LCP service implementing the Readium 3.x [LcpService] interface. Only [acquirePublication]
 * is exercised by the borrowing tests; the remaining members are stubbed.
 */

class MockLCPService(
  val publication: LcpService.AcquiredPublication? = null
) : LcpService {

  override suspend fun acquirePublication(
    lcpl: ByteArray,
    onProgress: (Double) -> Unit
  ): Try<LcpService.AcquiredPublication, LcpError> {
    return if (this.publication == null) {
      Try.failure(LcpError.LicenseProfileNotSupported)
    } else {
      Try.success(this.publication)
    }
  }

  override suspend fun acquirePublication(
    lcpl: File,
    onProgress: (Double) -> Unit
  ): Try<LcpService.AcquiredPublication, LcpError> {
    return if (this.publication == null) {
      Try.failure(LcpError.LicenseProfileNotSupported)
    } else {
      Try.success(this.publication)
    }
  }

  override suspend fun retrieveLicense(
    asset: Asset,
    authentication: LcpAuthenticating,
    allowUserInteraction: Boolean
  ): Try<LcpLicense, LcpError> {
    return Try.failure(LcpError.LicenseProfileNotSupported)
  }

  override suspend fun retrieveLicenseDocument(
    asset: ContainerAsset
  ): Try<LicenseDocument, LcpError> {
    return Try.failure(LcpError.LicenseProfileNotSupported)
  }

  override suspend fun injectLicenseDocument(
    licenseDocument: LicenseDocument,
    publicationFile: File
  ): Try<Unit, LcpError> {
    return Try.failure(LcpError.LicenseProfileNotSupported)
  }

  override fun contentProtection(
    authentication: LcpAuthenticating
  ): ContentProtection {
    throw UnsupportedOperationException("MockLCPService.contentProtection is not used in tests")
  }
}
