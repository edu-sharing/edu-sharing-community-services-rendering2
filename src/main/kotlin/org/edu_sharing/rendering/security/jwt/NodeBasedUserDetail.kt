package org.edu_sharing.rendering.security.jwt

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.Date

data class NodeBasedUserDetail(
    private val username: String,
    val node: String,
    private val notBefore: Date,
    private val expirationDate: Date,
    private val authorities: MutableCollection<out GrantedAuthority>
) : UserDetails {
    override fun getAuthorities(): MutableCollection<out GrantedAuthority> {
        return authorities
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
