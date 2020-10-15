package se.ltu.workflow.manager.arrowhead;

import java.net.InetSocketAddress;
import java.util.Objects;

import se.arkalix.security.identity.SystemIdentity;

/**
 * Basic representation of an Arrowhead system
 *
 */
public class AFSystem {
    
    String name;
    private final SystemIdentity identity;
    private final InetSocketAddress remoteSocketAddress;
    
    public AFSystem(String name, SystemIdentity identity, InetSocketAddress remoteSocketAddress) {
        this.name = Objects.requireNonNull(name, "Expected system name");
        this.identity = Objects.requireNonNull(identity,"Expected system certificate");
        this.remoteSocketAddress = Objects.requireNonNull(remoteSocketAddress,
                                                            "Expected system IP Socket Address");
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((identity == null) ? 0 : identity.hashCode());
        return result;
    }

    /**
     * Two Arrowhead Framework systems are equal if they have the same x.509 certificate
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AFSystem other = (AFSystem) obj;
        if (identity == null) {
            if (other.identity != null)
                return false;
        } else if (!identity.equals(other.identity))
            return false;
        return true;
    }
    
    
}
