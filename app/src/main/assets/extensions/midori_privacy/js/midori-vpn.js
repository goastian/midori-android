/*******************************************************************************

    Midori Privacy - read-only Midori VPN status bridge

    Midori Privacy asks the companion extension for its current state. The
    response comes directly from the extension ID below, so another extension
    cannot impersonate an active VPN connection.

*/

const VPN_EXTENSION_ID = 'midorivpn@astian.org';
const VPN_STATUS_ACTION = 'get-midori-vpn-status';
const VPN_STATUS_TTL = 30_000;
const vpnStates = new Set([ 'off', 'connecting', 'connected' ]);

let currentStatus = {
    state: 'off',
    updatedAt: 0,
};

const normalizeStatus = status => {
    if (
        status instanceof Object === false ||
        vpnStates.has(status.state) === false
    ) {
        return { state: 'off', updatedAt: 0 };
    }

    const updatedAt = Number.isFinite(status.updatedAt)
        ? status.updatedAt
        : Date.now();
    if ( status.state !== 'off' && Date.now() - updatedAt > VPN_STATUS_TTL ) {
        return { state: 'off', updatedAt: 0 };
    }

    return {
        state: status.state,
        updatedAt,
    };
};

const getMidoriVpnStatus = ( ) => {
    currentStatus = normalizeStatus(currentStatus);
    return { ...currentStatus };
};

const refreshMidoriVpnStatus = async ( ) => {
    try {
        const response = await browser.runtime.sendMessage(
            VPN_EXTENSION_ID,
            {
                action: VPN_STATUS_ACTION,
                source: 'midori-protection',
            }
        );
        currentStatus = normalizeStatus(response);
    } catch {
        currentStatus = { state: 'off', updatedAt: 0 };
    }
    return { ...currentStatus };
};

export { getMidoriVpnStatus, refreshMidoriVpnStatus };
