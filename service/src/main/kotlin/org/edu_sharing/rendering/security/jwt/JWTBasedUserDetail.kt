package org.edu_sharing.rendering.security.jwt

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonRootName
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.ArrayList
import java.util.Date

data class JWTBasedUserDetail(
    private var username: String,
    var notBefore: Date,
    var expirationDate: Date,
    private var authorities: MutableCollection<out GrantedAuthority>? = ArrayList<GrantedAuthority>(),
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
