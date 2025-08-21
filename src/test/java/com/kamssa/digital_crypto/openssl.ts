[ req ]
default_bits       = 2048
default_md         = sha256
distinguished_name = dn
req_extensions     = req_ext
prompt             = no

[ dn ]
C  = FR
ST = Paris
L  = Paris
O  = MyCompany
OU = IT
CN = myserver.example.com

[ req_ext ]
subjectAltName = @alt_names

[ alt_names ]
# DNSNAME
DNS.1 = myserver.example.com
DNS.2 = www.example.org

# RFC822NAME (email)
email.1 = admin@example.com
email.2 = support@example.org

# IPADDRESS
IP.1 = 192.168.1.10
IP.2 = 2001:db8::1

# URI
URI.1 = https://example.com/app
URI.2 = ftp://ftp.example.org/files

# OTHERNAME_GUID (OID Microsoft GUID)
otherName.1 = 1.3.6.1.4.1.311.25.1;UTF8:550e8400-e29b-41d4-a716-446655440000

# OTHERNAME_UPN (OID Microsoft UPN)
otherName.2 = 1.3.6.1.4.1.311.20.2.3;UTF8:user@domain.local

# DEFAULT (ici un DNS générique)
DNS.99 = default.example.com
