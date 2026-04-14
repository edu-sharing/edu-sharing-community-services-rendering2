package org.edu_sharing.rendering.security.jwt

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.*

data class JWTBasedUserDetail(
    private val username: String,
    val notBefore: Date,
    val expirationDate: Date,
    private val authorities: MutableCollection<out GrantedAuthority>? = ArrayList<GrantedAuthority>(),
    val repoId: String,
    val firstName: String,
    val lastName : String,
    val email : String,
    val primaryAffiliation: String
) : UserDetails {

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        return authorities ?: ArrayList()
    }

    override fun getPassword(): String {
        return ""
    }

    override fun getUsername(): String {
        return username
    }

    override fun isAccountNonExpired(): Boolean {
        return Date().time <= expirationDate.time
    }

    override fun isAccountNonLocked(): Boolean {
        return Date().time <= notBefore.time
    }

    override fun isCredentialsNonExpired(): Boolean {
        return Date().time <= expirationDate.time
    }

    override fun isEnabled(): Boolean {
        return true
    }

}
