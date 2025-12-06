---
title: 2. Koda Configuration
desc: Learn about self-hosting, configuration via environment variables, and authentication providers. Try Koda - It's free! See Koda's technical guide.
---

# Koda Configuration

This section explains the configuration options, both for self-hosting and developer setup.

<p class="advice">
Koda is configured using environment variables and flags.
</p>

## How the configuration works

Koda is configured using environment variables and flags. **Environment variables** start
with <code class="language-bash">KODA_</code>. **Flags** use the format
<code class="language-bash"><enable|disable>-<flag-name></code>.

Flags are used to enable/disable a feature or behaviour (registration, feedback),
while environment variables are used to configure the settings (auth, smtp, etc).
Flags and evironment variables are also used together; for example:

```bash
# This flag enables the use of SMTP email
KODA_FLAGS: [...] enable-smtp

# These environment variables configure the specific SMPT service
# Backend
KODA_SMTP_HOST: <host>
KODA_SMTP_PORT: 587
```

**Flags** are configured in a single list, no matter they affect the backend, the frontend,
the exporter, or all of them; on the other hand, **environment variables** are configured for
each specific service. For example:

```bash
KODA_FLAGS: [...] enable-login-with-google

# Backend
KODA_GOOGLE_CLIENT_ID: <client-id>
KODA_GOOGLE_CLIENT_SECRET: <client-secret>
```

Check the configuration guide for [Elestio][1] or [Docker][2]. Additionally, if you are using
the developer environment, you may override its values in the startup scripts,
as explained in the [Developer Guide][3].

**NOTE**: All the examples that have value represent the **default** value, and the
examples that do not have value are optional, and inactive or disabled by default.

## Telemetries

Koda uses anonymous telemetries from the self-hosted instances to improve the platform experience.
Consider sharing these anonymous telemetries enabling the corresponding flag:

```bash
KODA_FLAGS: [...] enable-telemetries
```

## Registration and authentication

There are different ways of registration and authentication in Koda:
- email/password
- Authentication providers like Google, Github or GitLab
- LDAP

You can choose one of them or combine several methods, depending on your needs.
By default, the email/password registration is enabled and the rest are disabled.

### Koda

This method of registration and authentication is enabled by default. For a production environment,
it should be configured next to the SMTP settings, so there is a proper registration and verification
process.

You may want to restrict the registrations to a closed list of domains,
or exclude a specific list of domains:

```bash
# Backend
# comma separated list of domains
KODA_REGISTRATION_DOMAIN_WHITELIST:

# Backend
# or a file with a domain per line
KODA_EMAIL_DOMAIN_WHITELIST: path/to/whitelist.txt
KODA_EMAIL_DOMAIN_BLACKLIST: path/to/blacklist.txt
```

__Since version 2.1__

Email whitelisting should be explicitly
enabled with <code class="language-bash">enable-email-whitelist</code> flag. For backward compatibility, we
autoenable it when <code class="language-bash">KODA_REGISTRATION_DOMAIN_WHITELIST</code> is set with
not-empty content.

Koda also comes with an option to completely disable the registration process;
for this, use the following flag:

```bash
KODA_FLAGS: [...] disable-registration
```

This option is only recommended for demo instances, not for production environments.

### Authentication Providers

To configure the authentication with third-party auth providers you will need to
configure Koda and set the correct callback of your Koda instance in the auth-provider
configuration.

The callback has the following format:

```html
https://<your_domain>/api/auth/oauth/<oauth_provider>/callback
```

You will need to change <your_domain> and <oauth_provider> according to your setup.
This is how it looks with Gitlab provider:

```html
https://<your_domain>/api/auth/oauth/gitlab/callback
```

#### Google

Allows integrating with Google as OAuth provider:

```bash
KODA_FLAGS: [...] enable-login-with-google

# Backend only:
KODA_GOOGLE_CLIENT_ID: <client-id>
KODA_GOOGLE_CLIENT_SECRET: <client-secret>
```

#### GitLab

Allows integrating with GitLab as OAuth provider:

```bash
KODA_FLAGS: [...] enable-login-with-gitlab

# Backend only
KODA_GITLAB_BASE_URI: https://gitlab.com
KODA_GITLAB_CLIENT_ID: <client-id>
KODA_GITLAB_CLIENT_SECRET: <client-secret>
```

#### GitHub

Allows integrating with GitHub as OAuth provider:

```bash
KODA_FLAGS: [...] enable-login-with-github

# Backend only
KODA_GITHUB_CLIENT_ID: <client-id>
KODA_GITHUB_CLIENT_SECRET: <client-secret>
```

#### OpenID Connect

__Since version 1.5.0__

Allows integrating with a generic authentication provider that implements the OIDC
protocol (usually used for SSO).

All the other options are backend only:

```bash
KODA_FLAGS: [...] enable-login-with-oidc

# Backend
KODA_OIDC_CLIENT_ID: <client-id>

# Mainly used for auto discovery the openid endpoints
KODA_OIDC_BASE_URI: <uri>
KODA_OIDC_CLIENT_SECRET: <client-id>

# Optional backend variables, used mainly if you want override; they are
# autodiscovered using the standard openid-connect mechanism.
KODA_OIDC_AUTH_URI: <uri>
KODA_OIDC_TOKEN_URI: <uri>
KODA_OIDC_USER_URI: <uri>

# Optional list of roles that users are required to have. If no role
# is provided, roles checking  disabled.
KODA_OIDC_ROLES: "role1 role2"

# Attribute to use for lookup roles on the user object. Optional, if
# not provided, the roles checking will be disabled.
KODA_OIDC_ROLES_ATTR:
```
<br />

__Since version 1.6.0__

Added the ability to specify custom OIDC scopes.

```bash
# This settings allow overwrite the required scopes, use with caution
# because Koda requres at least `name` and `email` attrs found on the
# user info. Optional, defaults to `openid profile`.
KODA_OIDC_SCOPES: "scope1 scope2"
```
<br />

__Since version 1.12.0__

Added the ability to specify the name and email attribute to use from
the userinfo object for the profile creation.

```bash
# Attribute to use for lookup the name on the user object. Optional,
# if not perovided, the `name` prop will be used.
KODA_OIDC_NAME_ATTR:

# Attribute to use for lookup the email on the user object. Optional,
# if not perovided, the `email` prop will be used.
KODA_OIDC_EMAIL_ATTR:
```
<br />

__Since version 1.19.0__

Introduced the ability to lookup the user info from the token instead
of making a request to the userinfo endpoint. This reduces the latency
of OIDC login operations and increases compatibility with some
providers that exposes some claims on tokens but not in userinfo
endpoint.

```bash
# Set the default USER INFO source. Can be `token` or `userinfo`. By default
# is unset (both will be tried, starting with token).

KODA_OIDC_USER_INFO_SOURCE:
```
<br />

__Since version 2.1.2__

Allows users to register and login with oidc without having to previously
register with another method.

```bash
KODA_FLAGS: [...] enable-oidc-registration
```

#### Azure Active Directory using OpenID Connect

Allows integrating with Azure Active Directory as authentication provider:

```bash
# Backend & Frontend
KODA_OIDC_CLIENT_ID: <client-id>

# Backend
KODA_OIDC_BASE_URI: https://login.microsoftonline.com/<tenant-id>/v2.0/
KODA_OIDC_CLIENT_SECRET: <client-secret>
```

### LDAP

Koda comes with support for *Lightweight Directory Access Protocol* (LDAP). This is the
example configuration we use internally for testing this authentication backend.

```bash
KODA_FLAGS: [...] enable-login-with-ldap

# Backend
KODA_LDAP_HOST: ldap
KODA_LDAP_PORT: 10389
KODA_LDAP_SSL: false
KODA_LDAP_STARTTLS: false
KODA_LDAP_BASE_DN: ou=people,dc=planetexpress,dc=com
KODA_LDAP_BIND_DN: cn=admin,dc=planetexpress,dc=com
KODA_LDAP_BIND_PASSWORD: GoodNewsEveryone
KODA_LDAP_USER_QUERY: (&(|(uid=:username)(mail=:username))(memberOf=cn=koda,ou=groups,dc=my-domain,dc=com))
KODA_LDAP_ATTRS_USERNAME: uid
KODA_LDAP_ATTRS_EMAIL: mail
KODA_LDAP_ATTRS_FULLNAME: cn
KODA_LDAP_ATTRS_PHOTO: jpegPhoto
```

## Koda URI

You will need to set the <code class="language-bash">KODA_PUBLIC_URI</code> environment variable in case you go to serve Koda to the users;
it should point to public URI where users will access the application:

```bash
# Backend
KODA_PUBLIC_URI: https://koda.mycompany.com

# Frontend
KODA_PUBLIC_URI: https://koda.mycompany.com

# Exporter
KODA_PUBLIC_URI: https://koda.mycompany.com
```

If you're using the official <code class="language-bash">docker-compose.yml</code> you only need to configure the
<code class="language-bash">KODA_PUBLIC_URI</code> envvar in the top of the file.

<p class="advice">
    If you plan to serve Koda under different domain than `localhost` without HTTPS,
    you need to disable the `secure` flag on cookies, with the `disable-secure-session-cookies` flag.
    This is a configuration NOT recommended for production environments; as some browser APIs do
    not work properly under non-https environments, this unsecure configuration
    may limit the usage of Koda; as an example, the clipboard does not work with HTTP.
</p>

## Email configuration

By default, <code class="language-bash">smtp</code> flag is disabled, the email will be
printed to the console, which means that the emails will be shown in the stdout.

Note that if you plan to invite members to a team, it is recommended that you enable SMTP
as they will need to login to their account after recieving the invite link sent an in email.
It is currently not possible to just add someone to a team without them accepting an
invatation email.

If you have an SMTP service, uncomment the appropriate settings section in
<code class="language-bash">docker-compose.yml</code> and configure those
environment variables.

Setting up the default FROM and REPLY-TO:

```bash
# Backend
KODA_SMTP_DEFAULT_REPLY_TO: Koda <no-reply@example.com>
KODA_SMTP_DEFAULT_FROM: Koda <no-reply@example.com>
```

Enable SMTP:

```bash
KODA_FLAGS: [...] enable-smtp

# Backend
KODA_SMTP_HOST: <host>
KODA_SMTP_PORT: 587
KODA_SMTP_USERNAME: <username>
KODA_SMTP_PASSWORD: <password>
KODA_SMTP_TLS: true
```

If you are not using SMTP configuration and want to log the emails in the console, you should use the following flag:

```bash
KODA_FLAGS: [...] enable-log-emails
```

## Valkey

The Valkey configuration is very simple, just provide a valid redis URI. Valkey is used
mainly for websocket notifications coordination.

```bash
# Backend
KODA_REDIS_URI: redis://localhost/0

# Exporter
KODA_REDIS_URI: redis://localhost/0
```

If you are using the official docker compose file, this is already configured.

## Demo environment

Koda comes with facilities to create a demo environment so you can test the system quickly.
This is an example of a demo configuration:

```bash
KODA_FLAGS: disable-registration enable-demo-users enable-demo-warning
```

**disable-registration** prevents any user from registering in the platform.
**enable-demo-users** creates users with a default expiration time of 7 days, and
once expired they are completely deleted with all the generated content.
From the registration page, there is a link with a `Create demo account` which creates one of these
users and logs in automatically.
**enable-demo-warning** is a modal in the registration and login page saying that the
environment is a testing one and the data may be wiped without notice.

Another way to work in a demo environment is allowing users to register but removing the
verification process:

```bash
KODA_FLAGS: disable-email-verification enable-demo-warning
```

## Air gapped environments

The current Koda installation defaults to several external proxies:
- to Github, from where the libraries and templates are downloaded
- to Google, from where the google-fonts are downloaded.

This is implemented as specific locations in the koda-front Nginx. If your organization needs to install Koda
in a 100% air-gapped environment, you can use the following configuration:

```bash
KODA_FLAGS: [...] enable-air-gapped-conf
```

When Koda starts, it will leave out the Nginx configuration related to external requests. This means that,
with this flag enabled, the Koda configuration will disable as well the libraries and templates dashboard and the use of Google fonts.

## Backend

This section enumerates the backend only configuration variables.

### Secret key

The <code class="language-bash">KODA_SECRET_KEY</code> envvar serves a master key from which other keys
for subsystems (eg http sessions, or invitations) are derived.

If you don't use it, all created sessions and invitations will become invalid on container restart
or service restart.

To use it, we recommend using a truly randomly generated 512 bits base64 encoded string here.
You can generate one with:

```bash
python3 -c "import secrets; print(secrets.token_urlsafe(64))"
```

And configure it:
```bash
# Backend
KODA_SECRET_KEY: my-super-secure-key
```

### Database

Koda only supports PostgreSQL and we highly recommend >=13 version. If you are using official
docker images this is already solved for you.

Essential database configuration:

```bash
# Backend
KODA_DATABASE_USERNAME: koda
KODA_DATABASE_PASSWORD: koda
KODA_DATABASE_URI: postgresql://127.0.0.1/koda
```

The username and password are optional. These settings should be compatible with the ones
in the postgres configuration:

```bash
# Postgres
POSTGRES_DATABASE: koda
POSTGRES_USER: koda
POSTGRES_PASSWORD: koda
```

### Storage

Storage refers to storing the user uploaded different objects in Koda (assets, file data,...).

Objects storage is implemented using "plugable" backends. Currently there are two
backends available: <code class="language-bash">fs</code> and <code class="language-bash">s3</code> (for AWS S3).

__Since version 2.11.0__
The configuration variables related to storage has been renamed, `KODA_STORAGE_ASSETS_*` are now `KODA_OBJECTS_STORAGE_*`.
`KODA_ASSETS_STORAGE_BACKEND` becomes `KODA_OBJECTS_STORAGE_BACKEND` and its values now are `fs` and `s3` instead of `assets-fs` or `assets-s3`.

#### FS Backend (default)

This is the default backend when you use the official docker images and the default
configuration looks like this:

```bash
# Backend
KODA_OBJECTS_STORAGE_BACKEND: fs
KODA_OBJECTS_STORAGE_FS_DIRECTORY: /opt/data/objects
```

The main downside of this backend is the hard dependency on nginx approach to serve files
managed by an application (not a simple directory serving static files). But you should
not worry about this unless you want to install it outside the docker container and
configure the nginx yourself.

In case you want understand how it internally works, you can take a look on the [nginx
configuration file][4] used in the docker images.

#### AWS S3 Backend

This backend uses AWS S3 bucket for store the user uploaded objects. For use it you should
have an appropriate account on AWS cloud and have the credentials, region and the bucket.

This is how configuration looks for S3 backend:

```bash
# Backend
AWS_ACCESS_KEY_ID: <you-access-key-id-here>
AWS_SECRET_ACCESS_KEY: <your-secret-access-key-here>
KODA_OBJECTS_STORAGE_BACKEND: s3
KODA_OBJECTS_STORAGE_S3_REGION: <aws-region>
KODA_OBJECTS_STORAGE_S3_BUCKET: <bucket-name>

# Optional if you want to use it with non AWS, S3 compatible service:
KODA_OBJECTS_STORAGE_S3_ENDPOINT: <endpoint-uri>
```

<p class="advice">
These settings are equally useful if you have a Minio storage system.
</p>

### File Data Storage

__Since version 2.11.0__

You can change the default file data storage backend with `KODA_FILE_DATA_BACKEND` environment variable. Possible values are:

- `legacy-db`: the current default backend, continues storing the file data of files and snapshots in the same location as previous versions of Koda (< 2.11.0), this is a conservative default behaviour and will be changed to `db` in next versions.
- `db`: stores the file data on an specific table (the future default backend).
- `storage`: stores the file data using the objects storage system (S3 or FS, depending on which one is configured)

This also comes with an additional feature that allows offload the "inactive" files on file storage backend and leaves the database only for the active files. To enable it, you should use the `enable-tiered-file-data-storage` flag and `db` as file data storage backend.

```bash
# Backend
KODA_FLAGS: [...] enable-tiered-file-data-storage
KODA_FILE_DATA_BACKEND: db
```

### Autosave

By default, Koda stores manually saved versions indefinitely; these can be found in the History tab and can be renamed, restored, deleted, etc. Additionally, the default behavior of on-premise instances is to not keep automatic version history. This automatic behavior can be modified and adapted to each on-premise installation with the corresponding configuration.

<p class="advice">
You need to be very careful when configuring automatic versioning, as it can significantly impact the size of your database. If you configure automatic versioning, you'll need to monitor this impact; if you're unsure about this management, we recommend leaving the default settings and using manual versioning.
</p>

This is how configuration looks for auto-file-snapshot

```bash
KODA_FLAGS: [...] enable-auto-file-snapshot               # Enable automatic version saving

# Backend
KODA_AUTO_FILE_SNAPSHOT_EVERY: 5             # How many save operations trigger the auto-save-version?
KODA_AUTO_FILE_SNAPSHOT_TIIMEOUT: "1h"       # How often is an automatic save forced even if the `every` trigger is not met?
```

Setting custom values for auto-file-snapshot does not change the behaviour for manual versions.

## Frontend

In comparison with backend, frontend only has a small number of runtime configuration
options, and they are located in the <code class="language-bash">\<dist>/js/config.js</code> file.

If you are using the official docker images, the best approach to set any configuration is
using environment variables, and the image automatically generates the <code class="language-bash">config.js</code> from
them.

In case you have a custom setup, you probably need to configure the following environment
variables on the frontend container:

To connect the frontend to the exporter and backend, you need to fill out these environment variables.

```bash
# Frontend
KODA_BACKEND_URI: http://your-koda-backend:6060
KODA_EXPORTER_URI: http://your-koda-exporter:6061
```

These variables are used for generate correct nginx.conf file on container startup.

## Other flags

There are other flags that are useful for a more customized Koda experience. This section has the list of the flags meant
for the user:

- <code class="language-bash">enable-cors</code>: Enables the default cors cofiguration that allows all domains
  (this configuration is designed only for dev purposes right now)
- <code class="language-bash">enable-backend-api-doc</code>: Enables the <code class="language-bash">/api/doc</code>
  endpoint that lists all rpc methods available on backend
- <code class="language-bash">disable-login-with-password</code>: allows disable password based login form
- <code class="language-bash">enable-prepl-server</code>: enables PREPL server, used by manage.py and other additional
  tools to communicate internally with Koda backend. Check the [CLI section][5] to get more detail.

__Since version 1.13.0__

- <code class="language-bash">enable-log-invitation-tokens</code>: for cases where you don't have email configured, this
  will log to console the invitation tokens.

__Since version 2.0.0__

- <code class="language-bash">disable-onboarding</code>: disables the onboarding modals.
- <code class="language-bash">disable-dashboard-templates-section</code>: hides the templates section from dashboard.
- <code class="language-bash">enable-webhooks</code>: enables webhooks. More detail about this configuration in [webhooks section][6].
- <code class="language-bash">enable-access-tokens</code>: enables access tokens. More detail about this configuration in [access tokens section][7].
- <code class="language-bash">disable-google-fonts-provider</code>: disables the google fonts provider.

[1]: /technical-guide/getting-started#configure-koda-with-elestio
[2]: /technical-guide/getting-started#configure-koda-with-docker
[3]: /technical-guide/developer/common#dev-environment
[4]: https://github.com/koda/koda/blob/main/docker/images/files/nginx.conf
[5]: /technical-guide/getting-started/docker#using-the-cli-for-administrative-tasks
[6]: /technical-guide/integration/#webhooks
[7]: /technical-guide/integration/#access-tokens
