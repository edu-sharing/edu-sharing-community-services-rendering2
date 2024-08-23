package org.edu_sharing.rendering.security

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.mockk
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.edu_sharing.rendering.security.jwt.JwtUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.context.SecurityContextRepository

@ExtendWith(MockKExtension::class)
class AuthTokenFilterTest {
    private val jwtUtils = mockk<JwtUtils>()
    private val securityContextRepository = mockk<SecurityContextRepository>()
    private val nodePermissionSessionContextRepository = mockk<NodePermissionSessionContextRepository>()

    lateinit var underTest: AuthTokenFilter

    @BeforeEach
    fun setup() {
        underTest = AuthTokenFilter(jwtUtils, securityContextRepository, nodePermissionSessionContextRepository)
    }

    @Test
    fun testDoFilterInternalManagesNodePermissionsAndContinuesInFilterChain() {
        // Arrange

        // Params
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>()
        val filterChain = mockk<FilterChain>()

        // Return value mocks
        val userDetails = mockk<UserDetails>()

        justRun { nodePermissionSessionContextRepository.validateSessionPermissions() }
        every {request.getHeader("Authorization")} returns "Bearer 123"
        every { userDetails.authorities } returns emptySet()


        // Act
        underTest.doFilter(request, response, filterChain)

    }
}