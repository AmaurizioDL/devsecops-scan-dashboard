package com.devsecops.dashboard;

/**
 * Categorias del OWASP Top 10:2021 (https://owasp.org/Top10/), usadas para dar
 * contexto de negocio y remediacion generica a un finding cuando el propio ZAP
 * no trae una solucion util. UNMAPPED cubre findings sin CWE o con un CWE que
 * no aparece en OwaspTop10Mapper.
 */
public enum OwaspTop10Category {

    A01_BROKEN_ACCESS_CONTROL(
            "A01:2021",
            "Broken Access Control",
            "Un atacante puede leer, modificar o borrar datos y funciones fuera de sus permisos, lo que puede derivar en fuga de datos de otros usuarios o toma de acciones administrativas no autorizadas.",
            "Aplicar controles de acceso en el servidor (deny-by-default), validar la propiedad del recurso en cada request, y anadir proteccion CSRF en operaciones que cambian estado."),

    A02_CRYPTOGRAPHIC_FAILURES(
            "A02:2021",
            "Cryptographic Failures",
            "Datos sensibles (credenciales, sesiones, informacion personal) pueden quedar expuestos en transito o en reposo si viajan sin cifrar o con algoritmos debiles, facilitando robo de identidad o cumplimiento normativo fallido.",
            "Forzar HTTPS/TLS en todas las rutas, usar algoritmos de cifrado y hashing modernos (ej. bcrypt/argon2 para contrasenas), y no transmitir ni loguear datos sensibles en claro."),

    A03_INJECTION(
            "A03:2021",
            "Injection",
            "Entrada no confiable interpretada como codigo (SQL, comandos de SO, plantillas) puede permitir a un atacante leer o modificar la base de datos, ejecutar comandos en el servidor, o comprometerlo por completo.",
            "Usar consultas parametrizadas / prepared statements, validar y sanear toda entrada del usuario, y evitar concatenar entrada de usuario en comandos, queries o plantillas."),

    A04_INSECURE_DESIGN(
            "A04:2021",
            "Insecure Design",
            "Fallas en el diseno (falta de limites de tasa, validaciones de negocio ausentes, exposicion de detalles internos en errores) permiten abuso funcional que un fix puntual de codigo no soluciona de raiz.",
            "Revisar el flujo afectado con modelado de amenazas, anadir limites de tasa/tamano donde aplique, y evitar exponer detalles internos (stack traces, rutas de archivo) en respuestas de error."),

    A05_SECURITY_MISCONFIGURATION(
            "A05:2021",
            "Security Misconfiguration",
            "Configuracion por defecto, servicios/paneles expuestos innecesariamente, o parsers XML mal configurados (XXE) dan a un atacante informacion o acceso que no deberia estar disponible.",
            "Endurecer la configuracion por defecto (deshabilitar paneles/endpoints de diagnostico en produccion, desactivar entidades externas en parsers XML), y mantener cabeceras de seguridad (CSP, X-Frame-Options, HSTS) activas."),

    A06_VULNERABLE_OUTDATED_COMPONENTS(
            "A06:2021",
            "Vulnerable and Outdated Components",
            "Componentes o dependencias con vulnerabilidades conocidas y publicas dan a un atacante un exploit ya documentado contra la aplicacion, sin necesidad de encontrar el bug el mismo.",
            "Inventariar dependencias (backend y frontend), actualizar a versiones sin CVEs conocidos, y retirar componentes sin mantenimiento."),

    A07_IDENTIFICATION_AUTH_FAILURES(
            "A07:2021",
            "Identification and Authentication Failures",
            "Sesiones o credenciales mal protegidas (fijacion de sesion, sin expiracion, credenciales por defecto/hardcodeadas) permiten a un atacante suplantar a un usuario legitimo.",
            "Invalidar y rotar el ID de sesion tras login, expirar sesiones inactivas, eliminar credenciales hardcodeadas/por defecto, y anadir proteccion contra fuerza bruta (rate limiting, MFA)."),

    A08_SOFTWARE_DATA_INTEGRITY_FAILURES(
            "A08:2021",
            "Software and Data Integrity Failures",
            "Deserializar datos no confiables o cargar codigo/dependencias sin verificar su integridad puede dar a un atacante ejecucion de codigo arbitrario en el servidor.",
            "Evitar deserializar datos de origen no confiable sin validacion, y verificar la integridad (firmas/checksums) de dependencias y actualizaciones antes de usarlas."),

    A09_LOGGING_MONITORING_FAILURES(
            "A09:2021",
            "Security Logging and Monitoring Failures",
            "Sin logging ni alertas suficientes, un ataque en curso (o ya exitoso) puede pasar desapercibido durante mucho tiempo, ampliando el dano y dificultando la respuesta a incidentes.",
            "Loguear eventos de seguridad relevantes (logins fallidos, cambios de permisos, errores de validacion) con suficiente contexto, y alertar sobre patrones anomalos en tiempo real."),

    A10_SSRF(
            "A10:2021",
            "Server-Side Request Forgery (SSRF)",
            "Si el servidor puede ser inducido a hacer peticiones a URLs elegidas por el atacante, este puede alcanzar sistemas internos (metadata de la nube, servicios administrativos) no expuestos directamente a Internet.",
            "Validar y restringir (allowlist) los destinos a los que el servidor puede hacer requests salientes, y aislar de red los servicios internos que no deban ser alcanzables desde la aplicacion."),

    UNMAPPED(
            "N/A",
            "Sin categoria OWASP Top 10 asociada",
            "Este finding no tiene un CWE reconocido en el catalogo de mapeo del proyecto, por lo que no se pudo asociar automaticamente a una categoria del OWASP Top 10:2021. Revisar manualmente su impacto.",
            "Revisar la descripcion y solucion original reportada por el scanner para definir la remediacion adecuada.");

    private final String code;
    private final String title;
    private final String businessImpact;
    private final String defaultRemediation;

    OwaspTop10Category(String code, String title, String businessImpact, String defaultRemediation) {
        this.code = code;
        this.title = title;
        this.businessImpact = businessImpact;
        this.defaultRemediation = defaultRemediation;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String businessImpact() {
        return businessImpact;
    }

    public String defaultRemediation() {
        return defaultRemediation;
    }

    public String label() {
        return code + " - " + title;
    }
}
