package com.aztu.support_erp.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/** Authentication backed by a validated SSO token; principal is the {@link SsoUser}. */
public class SsoAuthenticationToken extends AbstractAuthenticationToken {

    private final SsoUser principal;

    public SsoAuthenticationToken(SsoUser principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override public Object getCredentials() { return null; }
    @Override public Object getPrincipal() { return principal; }
}
