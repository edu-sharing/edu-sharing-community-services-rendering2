package org.edu_sharing.rendering.security.jwt

/**
@ExtendWith(MockKExtension::class)
class JwtUtilsTest {
    private val jwtParser = mockk<JwtParser>()

    private lateinit var underTest: JwtUtils

    @BeforeEach
    fun setup() {
        underTest = JwtUtils(jwtParser)
        clearAllMocks()
    }

    @Test
    fun testValidateJwtReturnsTrueForValidJwt() {
        // Arrange
        val token = "qwe"
        every {jwtParser.parse(token)} returns mockk<Jwt<Header, String>>()

        // Act
        val result = underTest.validateJwtToken(token)

        // Assert
        assert(result)
        verify (exactly = 1){ jwtParser.parse(token) }
        confirmVerified(jwtParser)
    }

    @Test
    fun testValidateJwtReturnsFalseOnMalformedJwtException() {
        // Arrange
        val token = "qwe"
        every {jwtParser.parse(token)} throws MalformedJwtException("test")

        // Act
        val result = underTest.validateJwtToken(token)

        // Assert
        assert(!result)
        verify (exactly = 1){ jwtParser.parse(token) }
        confirmVerified(jwtParser)
    }

    @Test
    fun testValidateJwtReturnsFalseOnExpiredJwtException() {
        // Arrange
        val token = "qwe"
        val header = DefaultHeader(mutableMapOf("Authorization" to "Bearer $token"))
        val claims = DefaultClaims(mapOf("test" to "test"))
        every {jwtParser.parse(token)} throws ExpiredJwtException(header, claims,"test")

        // Act
        val result = underTest.validateJwtToken(token)

        // Assert
        assert(!result)
        verify (exactly = 1){ jwtParser.parse(token) }
        confirmVerified(jwtParser)
    }

    @Test
    fun testValidateJwtReturnsFalseOnUnsupportedJwtException() {
        // Arrange
        val token = "qwe"
        every {jwtParser.parse(token)} throws UnsupportedJwtException("test")

        // Act
        val result = underTest.validateJwtToken(token)

        // Assert
        assert(!result)
        verify (exactly = 1){ jwtParser.parse(token) }
        confirmVerified(jwtParser)
    }

    @Test
    fun testValidateJwtReturnsFalseOnIllegalArgumentException() {
        // Arrange
        val token = "qwe"
        every {jwtParser.parse(token)} throws IllegalArgumentException("test")

        // Act
        val result = underTest.validateJwtToken(token)

        // Assert
        assert(!result)
        verify (exactly = 1){ jwtParser.parse(token) }
        confirmVerified(jwtParser)
    }

    @Test
    fun testValidateJwtReturnsFalseOnInvalidKeyException() {
        // Arrange
        val token = "qwe"
        every {jwtParser.parse(token)} throws InvalidKeyException("test")

        // Act
        val result = underTest.validateJwtToken(token)

        // Assert
        assert(!result)
        verify (exactly = 1){ jwtParser.parse(token) }
        confirmVerified(jwtParser)
    }

    @Test
    fun testGetUserDetailsFromJwtProperlyMapsDataFromJwt() {
        // Arrange
        val token = "qwe"
        val jwtObj = mockk<Jws<Claims>>()
        val claims = mockk<Claims>()
        val timestampNotBefore = Timestamp(1723195060097)
        val notBefore = Date(timestampNotBefore.time)
        val timestampExpiration = Timestamp(1723195070097)
        val expiration = Date(timestampExpiration.time)
        every {claims.issuer} returns "testUser"
        every {claims.notBefore} returns notBefore
        every {claims.expiration} returns expiration
        every {jwtObj.payload} returns claims
        every {jwtParser.parseSignedClaims(token)} returns jwtObj

        excludeRecords {
            claims.issuer
            claims.notBefore
            claims.expiration
            jwtObj.payload
        }

        // Act
        val result = underTest.getUserDetailsFromJwt(token)

        // Assert
        assert(result.notBefore == notBefore)
        assert(result.expirationDate == expiration)
        assert(result.username == "testUser")
        assert(result.authorities.isEmpty())

        verify (exactly = 1){ jwtParser.parseSignedClaims(token) }
        confirmVerified(jwtParser)
    }

    @Test
    fun testGetNodePermissionsProperlyMapsDataFromJwt() {
        // Arrange
        val token = "qwe"
        val jwtObj = mockk<Jws<Claims>>()
        val claims = mockk<Claims>()

        every {claims.get("node", String::class.java)} returns "node123"
        every {claims.get("permissions", List::class.java)} returns listOf("permission1", "permission2", "permission3")
        every {claims.get("mimeType", String::class.java)} returns "application/json"
        every {claims.get("mediaType", String::class.java)} returns "json"
        every {jwtObj.payload} returns claims
        every {jwtParser.parseSignedClaims(token)} returns jwtObj

        excludeRecords {
            claims.get("node", String::class.java)
            claims.get("permissions", List::class.java)
            claims.get("mimeType", String::class.java)
            claims.get("mediaType", String::class.java)
            jwtObj.payload
        }

        // Act
        val result = underTest.getNodePermissions(token)

        // Assert
        assert(result.nodeId == "node123")
        assert(result.permissions == setOf("permission1", "permission2", "permission3"))
        assert(result.mimeType == "application/json")
        assert(result.mediaType == "json")

        verify (exactly = 1){ jwtParser.parseSignedClaims(token) }
        confirmVerified(jwtParser)
    }


}
        */