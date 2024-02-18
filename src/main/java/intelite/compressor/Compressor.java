package intelite.compressor;
/**
 *
 * @author Jose Gabriel
 */
import intelite.hilos.HiloCanal;
import intelite.models.Canal;
import intelite.models.Config;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import org.apache.commons.lang3.math.Fraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.FileUtils;


public class Compressor {
    
    private static final Logger LOG = LoggerFactory.getLogger(Compressor.class);
    private static final DateFormat DF = new SimpleDateFormat("dd/MM/yyyy-HH:mm:ss: ");
    public static final String DS = System.getProperty("file.separator").equals("\\") ? "\\" : "/";
    public static final String FFMPEG_PATH = System.getProperty("user.dir") + DS;
    public static final String PROPERTIES_PATH = System.getProperty("user.dir") + DS;
    public static final String DIR_TMP = "COMPRIMIDOS" + DS;
    public static final boolean ISLINUX = DS.equals("/");
    
    private static final String CONTENT_CONFIG
             = "#### DESCOMENTAR LÍNEAS QUE INICIEN CON #> (QUITAR # y >) #####\n\n"
            + "# ********** CONFIGURACIÓN GENERAL **********\n"
            + "\n"
            + "# ----- Formato de salida -----\n"
            + "# Valores: mp4 y wmv\n"
            + "# Descripción: establece el formato de salida de la conversión (valor por defecto: mp4)\n"
            + ">fto=mp4\n"
            + "# -------------------------\n"
            + "\n"
            + "# ----- Custom Settings  -----\n"
            + "# Valores: on y off\n"
            + "# Descripción: indica si para la conversión se tomarán las propiedades de origen o los ajustes personalizados de audio y video (valor por defecto: off)\n"
            + ">custom=off\n"
            + "\n"
            + "# ********** CONFIGURACIÓN DE VIDEO **********\n"
            + "\n"
            + "# ----- Resolución -----\n"
            + "# Valores: 640x480, 720x576, 1280x720, 1920x1080\n"
            + "# Descripción: establece la resolución de salida del video (valor por defecto: 1280x720)\n"
            + ">res=640x480\n"
            + "# -------------------------\n"
            + "\n"
            + "# ----- Bitrate video (kbps) -----\n"
            + "# Valores: un valor entero (valor sugerido entre 100 y 1000)\n"
            + "# Descripción: establece el bitrate del video (valor por defecto: 750)\n"
            + ">vbr=750\n"
            + "# -------------------------\n"
            + "\n"
            + "# ----- Fotogramas por segundo (fps) -----\n"
            + "# Valores: un valor entero (valores sugeridos: 24, 25, 30, 60)\n"
            + "# Descripción: indica el valor de la propiedad fps del video (valor por defecto: 30)\n"
            + ">fps=30\n"
            + "# -------------------------\n"
            + "\n"
            + "# ********** CONFIGURACIÓN DE AUDIO **********\n"
            + "\n"
            + "# ----- Samplerate audio (Hz) -----\n"
            + "# Valores: un valor entero (valores sugeridos: 32000, 44100, 48000)\n"
            + "# Descripción: indica la velocidad de muestra de sonido (valor por defecto: 32000)\n"
            + ">asr=32000\n"
            + "# -------------------------\n"
            + "\n"
            + "# ----- Bitrate audio (kbps) -----\n"
            + "# Valores: un valor entero (valores sugeridos: 48, 64, 96, 128, 160, 192, 256)\n"
            + "# Descripción: establece el bitrate del audio (valor por defecto: 96)\n"
            + ">abr=96\n"
            + "# -------------------------\n"
            + "\n"
            + "\n\n";

    
    private static final String CONTENT_CANALES
            = "# ********** CONFIGURACIÓN DE CANALES A GRABAR **********\n"
            + "\n"
            + "# ===== Campos que conforman el registro de cada canal: =====\n"
            + "# CANAL: establece el nombre del canal, debe contener solo letras, números y/o guiones bajos o medios (p. ej. 34-2). Este campo no puede estar vacio.\n"
            + "# ALIAS: establece el alias del canal, usado para crear la carpeta del canal, debe contener solo letras, números y/o guiones bajos o medios (p. ej. TV34-2b). Este campo no puede estar vacio.\n"
            + "# ORIGEN: indica el origen de la grabación, origen del streaming (p. ej. http://99.90.149.52/live.m3u8). Este campo no puede estar vacio.\n"
            + "# DESTINO: indica el directorio de destino de la grabación (p. ej. SO WINDOWS=C:\\\\Users\\\\Usuario\\\\GrabacionesTV, SO LINUX=/opt/GrabacionesTV). Este campo no puede estar vacio. \n"
            + "# ACTIVO: indica si el canal está activo para grabarse, los valores pueden 0 (inactivo) y 1 (activo). Si no se indica un valor, el valor por defecto será 1 (activo).  \n"
            + "\n"
            + "# ===== Formato para registrar un canal: =====\n"
            + "# CANAL=ALIAS, ORIGEN, DESTINO, ACTIVO\n"
            + "\n"
            + "# ===== Ejemplo de registro de un canal en SO Windows: =====\n"
            + "# 34-2=TV34-2b, http://99.90.149.52/live.m3u8, C:\\\\Users\\\\Usuario\\\\GrabacionesTV, 1\n"
            + "\n"
            + "# ===== Ejemplo de registro de un canal en SO Linux: =====\n"
            + "# 34-2=TV34-2b, http://99.90.149.52/live.m3u8, /opt/GrabacionesTV, 1\n"
            + "\n"
            + "\n"
            + "# /////////////// REGISTRO DE CANALES A GRABAR ///////////////";

              
    private static final String CONTENT_HORARIOS
            = "# ********** CONFIGURACIÓN DE HORARIOS DE GRABACIÓN **********\n"
            + "\n"
            + "# ===== Campos que conforman el registro de un horario: =====\n"
            + "# CANAL: indica el nombre del canal, debe coincidir con el registrado en el archivo \"canales.properties\" \n"
            + "# DIA: un valor entero entre 1 y 7 que indica el día de la semana (Domingo=1, Lunes=2, Martes=3, Miercoles=4, Jueves=5, Viernes=6, Sabado=7)\n"
            + "# HORA_INI: indica la hora de inicio para deshabilitar el proceso de grabación\n"
            + "# HORA_FIN: indica hasta que hora durará deshabilitado el proceso de grabación\n"
            + "\n"
            + "# ===== Formato para registrar un horario: =====\n"
            + "# CANAL=DIA>HORA_INI-HORA_FIN\n"
            + "\n"
            + "# ===== Ejemplo de registro de un canal en SO Windows: =====\n"
            + "# 34-2=1>2-5, 2>2-5, 3>2-5, 4>2-5, 5>2-5, 6>2-5, 7>2-5 (indica que de lunes a viernes no se va a grabar de las 02:00 a las 05:00 hrs.)\n"
            + "\n"
            + "\n"
            + "# [Domingo=1, Lunes=2, Martes=3, Miercoles=4, Jueves=5, Viernes=6, Sabado=7, DIARIO=8]\n"
            + "# ===== HORARIO GENERAL (APLICA PARA TODOS LOS CANALES)  =====\n"
            + "#all=8>2-5\n"
            + "\n"
            + "# /////////////// HORARIO ESPECIFICO (APLICA SOLO PARA EL CANAL INDICADO) ///////////////\n"
            + "#34-2=6>10-11\n"
            + "#c1=1>2-5, 2>2-5, 3>2-5, 4>2-5, 5>2-5, 6>2-5, 7>2-5\n"
            + "#cN=1>2-5, 2>2-5, 3>2-5, 4>2-5, 5>2-5, 6>2-5, 7>2-5\n"
            + "";
            
    public static void main(String[] args) {
        LOG.error("===== Compresor v.20230131=====\n");
        
        Properties conf = new Properties();
        Properties canales = new Properties();
        Properties horarios = new Properties();
        
        String path_conf = PROPERTIES_PATH + "config.properties";
        File conf_file = new File(path_conf);
        if (!conf_file.exists()) {
            System.out.println(DF.format(new Date()) + "Creando archivo config.properties...");
            Properties p = new Properties();
            try {
                p.store(new FileWriter("config.properties"), CONTENT_CONFIG);
            } catch (IOException e) {
                LOG.error("No se pudo crear el archivo 'config.properties' en la ruta " + path_conf, e);
            }
        }
        
        String path_canales = PROPERTIES_PATH + "canales.properties";
        File canales_file = new File(path_canales);
        if(!canales_file.exists()){
            System.out.println(DF.format(new Date()) + "Creando archivo canales.properties...");
            Properties p = new Properties();
            try{
                p.store(new FileWriter("canales.properties"), CONTENT_CANALES);
            } catch (IOException e) {
                LOG.error("No se pudo crear el archivo 'canales.properties' en la ruta " + path_canales, e);
            }
        }
        
        String path_horarios = PROPERTIES_PATH + "horarios.properties";
        File horarios_file = new File(path_horarios);
        if (!horarios_file.exists()) {
            LOG.debug("Creando archivo horarios.properties...");
            Properties p = new Properties();
            try {
                p.store(new FileWriter("horarios.properties"), CONTENT_HORARIOS);
            } catch (IOException e) {
                LOG.error("No se pudo crear el archivo 'horarios.properties' en la ruta " + path_horarios, e);
            }
        }
        
        try { 
            conf.load(new FileReader(path_conf));
            canales.load(new FileReader(path_canales));
            horarios.load(new FileReader(path_horarios));
            
            iniciarCompactacion(conf, canales, horarios);
        } catch (FileNotFoundException e){
            LOG.error("No se pudo encontrar o cargar algunos de los archivos de configuracion!", e);
            close();
        } catch (IOException e ){
            LOG.error("No se pudo encontrar o cargar alguno de los archivos de configuracion! ", e);
            close();
        }
    }
    
    private static void iniciarCompactacion(Properties conf, Properties canales, Properties horarios) {
        String fto = conf.getProperty("fto", "mp4").toLowerCase();

        String custom = conf.getProperty("custom", "off").toLowerCase();

        String vbr_s = conf.getProperty("vbr", "500");
        Long vbr = Long.parseLong(vbr_s) * 1000;

        String fps_s = conf.getProperty("fps", "24");
        Fraction fps = Fraction.getFraction(Integer.parseInt(fps_s), 1);


        String abr_s = conf.getProperty("abr", "48");
        Long abr = Long.parseLong(abr_s) * 1000;
        
        // Se crea el objeto de tipo Config
        Config config = new Config(fto, custom, vbr, fps, abr);

        System.out.println("\n*** CONFIGURACIÓN ***");
        System.out.println("> Formato de salida: " + fto);
        System.out.println("> Custom settings: " + custom.toUpperCase());

        if (custom.equals("off")) {
            System.out.println("> Bitrate video: " + vbr_s + " kbps");
            System.out.println("> Fotogramas por segundo: " + fps_s + " fps");
            System.out.println("> Bitrate audio: " + abr_s + " kbps");
        } else {
            System.out.println("> Bitrate video: " + vbr_s + " kbps");
            System.out.println("> Fotogramas por segundo: " + fps_s + " fps");
            System.out.println("> Bitrate audio: " + abr_s + " kbps");
        }
        System.out.println("");

        System.out.println(DF.format(new Date()) + "Inicia obtención de canales a comprimir...");
        Enumeration<Object> keys = canales.keys();
        if (keys.hasMoreElements()) {
            // Se crea el directorio temporal si es que aún no existe 
            File carpeta_tmp = new File(DIR_TMP);
            Boolean bseguir = true;
            if (!carpeta_tmp.exists()) {
                // Si no existe, se crea la carpeta de salida
                if (ISLINUX) {
//                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxrwx"); // 777
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x"); // 755
                    try {
                        Files.createDirectories(Paths.get(DIR_TMP), PosixFilePermissions.asFileAttribute(perms));
                        System.out.println(DF.format(new Date()) + "SE HA CREADO LA CARPETA TEMPORAL -> " + DIR_TMP);
                    } catch (IOException e) {
                        bseguir = false;
                        LOG.warn("ERROR AL CREAR LA CARPETA TEMPORAL -> " + DIR_TMP);
                        LOG.warn("Error al crear la carpeta temporal > iniciarConversion:", e);
                    }
                } else {
                    if (carpeta_tmp.mkdirs()) {
                        System.out.println(DF.format(new Date()) + "SE HA CREADO LA CARPETA TEMPORAL -> " + carpeta_tmp.getAbsolutePath());
                    } else {
                        bseguir = false;
                        LOG.info("ERROR AL CREAR LA CARPETA TEMPORAL -> " + carpeta_tmp.getAbsolutePath());
                    }
                }
            }

            if (bseguir) {
                // Se crean y ejecutan los hilos de los canales activos
                while (keys.hasMoreElements()) {
                    
                    Object key = keys.nextElement();
                    String[] d = canales.get(key).toString().trim().replace(" ", "").split(",");
                    String nombre = key.toString();
                    String alias = d[0];
                    String origen = setBackslash(d[1]);
                    String destino = setBackslash(d[2]);
                    Integer activo = d.length > 3 ? Integer.parseInt(d[3]) : 1;
                    // Se obtienen los horarios
                    String[] horario_g = {};
                    String[] horario_e = {};
                    String all = horarios.getProperty("all", "");
                    // Se obtiene el horario general
                    if (!all.equals("")) {
                        horario_g = all.trim().replace(" ", "").split(",");
                    }
                    // Se obtiene el horario especifico
                    String h = horarios.getProperty(key.toString(), "");
                    if (!h.equals("")) {
                        horario_e = horarios.get(key).toString().trim().replace(" ", "").split(",");
                    }

                    // Se crea el objeto canal respectivo
                    Canal canal = new Canal(nombre, alias, origen, destino, activo, horario_g, horario_e);
                    // Creación del hilo del canal
                    if (canal.getActivo().equals(1)) {
                        System.out.println(DF.format(new Date()) + "Creando hilo canal " + nombre + "...");
                        
                      
                        HiloCanal hilo_canal = new HiloCanal("Hilo " + nombre, canal, config, origen);
                        System.out.println(DF.format(new Date()) + hilo_canal.getName() + " creado!");
                        System.out.println(DF.format(new Date()) + "Ejecutando proceso de compresion del canal " + nombre + "...");
                        hilo_canal.start();
                    }
                }
            }
        } else {
            LOG.info("No hay canales registrados o activos para convertir en el archivo canales.properties!");
            
        }
    }

    private static String setBackslash(String cadena) {
        if (cadena != null && !cadena.equals("")) {
            int length = cadena.length();
            String backslash = cadena.substring(length - 1);
            if (!backslash.equals(DS)) {
                cadena += DS;
            }
        }
        return cadena;
    }

    
    private static void close() {
        LOG.debug("Terminando ejecución Compresor.....");
        System.exit(0);
    }
    
 private static long obtenerDuracionVideosEnRuta(String rutaDirectorio) {
        File directorio = new File(rutaDirectorio);
        if (!directorio.exists() || !directorio.isDirectory()) {
            return 0; // Directorio no válido o no existe
        }
        
        long duracionTotal = 0;
        File[] archivos = directorio.listFiles();
        if (archivos != null) {
            for (File archivo : archivos) {
                if (archivo.isFile()) {
                    // Calcular la duración del archivo usando Apache Commons IO
                    try {
                        duracionTotal += FileUtils.getFile(archivo).length();
                    } catch (Exception e) {
                        LOG.error("Error al obtener la duración del archivo: " + archivo.getAbsolutePath(), e);
                    }
                }
            }
        }
        
        return duracionTotal;
    }

}
