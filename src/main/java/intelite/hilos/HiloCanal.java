package intelite.hilos;

import static intelite.compressor.Compressor.DS;
import intelite.compressor.Compressor;
import intelite.models.Canal;
import intelite.models.Config;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import net.bramp.ffmpeg.progress.Progress;
import net.bramp.ffmpeg.progress.ProgressListener;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HiloCanal extends Thread {
    
    private static final Logger LOG = LoggerFactory.getLogger(HiloCanal.class);
    private static final DateFormat DF = new SimpleDateFormat("dd/MM/yyyy-HH:mm:ss: ");
    private final DateFormat df_anio = new SimpleDateFormat("yyyy");
    private final DateFormat df_mes = new SimpleDateFormat("MM");
    private final DateFormat df_dia = new SimpleDateFormat("dd");
    private Canal canal;
    private Config config;
    private String rutaOrigen;
    private List<String> archivosComprimidos = new ArrayList<>();
    
    CopyOption[] options_move = new CopyOption[]{
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE
    };
    
    public HiloCanal() {
        super();
    }
    
    public HiloCanal(String name, Canal canal, Config config, String rutaOrigen) {
        super(name);
        this.canal = canal;
        this.config = config;
        this.rutaOrigen = rutaOrigen;
    }
    
    @Override
    public void run() {
        while (!Thread.interrupted()) {
            procesarArchivos();
        }
        long duracionTotal = obtenerDuracionVideosEnRuta(rutaOrigen);
        System.out.println("Duración total de los videos en la ruta " + rutaOrigen + ": " + duracionTotal + " bytes");
        
    }

    private void procesarArchivos() {
        Calendar today = Calendar.getInstance();
        List<String[]> files_today = obtenerListaArchivos(today.getTime());
        
        Calendar yesterday = Calendar.getInstance();
        yesterday.add(Calendar.DATE, -1);
        
        List<String[]> files_yesterday = obtenerListaArchivos(yesterday.getTime());

        List<String[]> files_procesar = new ArrayList<>();
        files_procesar.addAll(files_today);
        files_procesar.addAll(files_yesterday);

        if (files_procesar.isEmpty()) {
            System.out.println(DF.format(new Date()) + "Esperando archivos para procesar del canal " + canal.getNombre() + "...");
        } else {
            files_procesar.forEach((data_file) -> {
                String input = data_file[0];
                String out = data_file[2];
                String filein = data_file[3];
                String fileout = data_file[4];
                String comprimir = data_file[5];

                if (!archivosComprimidos.contains(filein)) { // Verificar si el archivo ya ha sido comprimido
                    Path path_out = Paths.get(out);

                    if (comprimir.equals("true")) {
                        try {
                            FFmpegBuilder builder = construirFFmpegBuilder(input, out);
                            FFmpeg ffmpeg = new FFmpeg("ffmpeg");
                            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg);

                            System.out.println(DF.format(new Date()) + "COMPRIMIENDO archivo " + filein + " ---> " + fileout + "...");
                            
                            // Monitorear el progreso de la tarea de FFmpeg
                                FFmpegJob job = executor.createJob(builder, new ProgressListener() {
                                long duration = obtenerDuracionVideo(input);
                                final double duration_ns = duration * TimeUnit.SECONDS.toNanos(1);

                                @Override
                                public void progress(Progress progress) {
                                    double percentage = progress.out_time_ns / duration_ns;
                                    String line = String.format(
                                            "Comprimiendo archivo -> %s... %.0f%% [%s]",
                                            filein,
                                            percentage * 100,
                                            progress.status
                                    );
                                    LOG.info(line);
                                }

                                private long obtenerDuracionVideo(String rutaArchivo) {
                                    try {
                                        ProcessBuilder builder = new ProcessBuilder("ffprobe", "-i", rutaArchivo, "-show_entries", "format=duration", "-v", "quiet", "-of", "csv=p=0");
                                        Process process = builder.start();
                                        InputStream inputStream = process.getInputStream();
                                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                        String line = reader.readLine();
                                        if (line != null) {
                                            return (long) Double.parseDouble(line) * 1000; // Duración en milisegundos
                                        }
                                    } catch (IOException e) {
                                        LOG.error("Error al obtener la duración del archivo de video: " + rutaArchivo, e);
                                    }
                                    return 0;
                                }
                            });

                            // Ejecutar la tarea de FFmpeg con el listener de progreso
                            
                            job.run();

                            System.out.println(DF.format(new Date()) + "ARCHIVO COMPRIMIDO -> " + fileout);

                            // Agregar el archivo de la lista de archivos comprimidos
                            archivosComprimidos.add(filein);

                        } catch (IOException e) {
                            LOG.warn("IOException procesarArchivos:", e);
                        }

                    } else {
                        File file_in = new File(input);
                        if (file_in.exists() && file_in.isFile()) {
                            moverArchivo(file_in.toPath(), path_out, fileout);
                            verificarCompresion(filein, fileout);
                        }
                    }
                }
            });
        }
    }

    private void moverArchivo(Path origen, Path destino, String archivoDestino) {
        try {
            Files.move(origen, destino.resolve(archivoDestino), options_move);
            System.out.println(DF.format(new Date()) + "ARCHIVO MOVIDO -> " + archivoDestino);
        } catch (IOException e) {
            LOG.warn("IOException moverArchivo:", e);
        }
    }

    private void verificarCompresion(String filein, String fileout) {
        try {
            File originalFile = new File(filein);
            File compressedFile = new File(fileout);

            if (originalFile.exists() && compressedFile.exists()) {
                if (originalFile.length() == compressedFile.length()) {
                    System.out.println(DF.format(new Date()) + "COMPRESIÓN EXITOSA -> " + fileout);
                    if (originalFile.delete()) {
                        System.out.println(DF.format(new Date()) + "ARCHIVO ORIGINAL ELIMINADO -> " + filein);
                    } else {
                        LOG.warn("No se pudo eliminar el archivo original -> " + filein);
                    }
                } else {
                    LOG.warn("COMPRESIÓN FALLIDA -> " + fileout);
                    System.out.println(DF.format(new Date()) + "INTENTANDO NUEVAMENTE LA COMPRESIÓN -> " + filein);
                    Files.deleteIfExists(Paths.get(fileout));
                    procesarArchivos();
                }
            } else {
                LOG.warn("No se encontraron archivos originales o comprimidos -> " + filein + ", " + fileout);
            }
        } catch (IOException e) {
            LOG.error("Error al verificar la compresión -> " + filein, e);
        }
    }

    private FFmpegBuilder construirFFmpegBuilder(String input, String out) {
        String fileExtension = obtenerExtensionArchivo(input);

        FFmpegBuilder builder;

        switch (fileExtension) {
            case "mp3":
                builder = construirFFmpegBuilderMP3(input, out);
                break;
            case "mp4":
                builder = construirFFmpegBuilderMP4(input, out);
                break;
            default:
                builder = construirFFmpegBuilderDefault(input, out);
                break;
        }
        return builder;
    }

    private FFmpegBuilder construirFFmpegBuilderMP3(String input, String out) {
        return new FFmpegBuilder()
                .setInput(input)
                .overrideOutputFiles(true)
                .addOutput(out)
                .setFormat("mp3")
                .setAudioCodec("libmp3lame")
                .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL)
                .done();
    }

    private FFmpegBuilder construirFFmpegBuilderMP4(String input, String out) {
        return new FFmpegBuilder()
                .setInput(input)
                .overrideOutputFiles(true)
                .addOutput(out)
                .setFormat("mp4")
                .setVideoCodec("libx264")
                .setVideoFrameRate(30) // Fps
                .setVideoResolution(680, 480) // Establece la resolución del video a 680x480
                .addExtraArgs("-b:v", "250 k") // Establece la velocidad de bits del video a 800Kbps
                .setAudioCodec("aac")
                .addExtraArgs("-b:a", "48k") // Establece la velocidad de bits del audio a 64Kbps
                .setStrict(FFmpegBuilder.Strict.EXPERIMENTAL)
                .done();
    }

    private FFmpegBuilder construirFFmpegBuilderDefault(String input, String out) {
        return new FFmpegBuilder()
                .setInput(input)
                .overrideOutputFiles(true)
                .addOutput(out)
                .done();
    }

    private String obtenerExtensionArchivo(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }

    private List<String[]> obtenerListaArchivos(Date fecha) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String fechaDirectorio = dateFormat.format(fecha);

        List<String[]> listaArchivos = new ArrayList<>();

        try {
            Files.walk(Paths.get(canal.getOrigen()))
                .filter(Files::isRegularFile)
                .forEach(filePath -> {
                    String rutaArchivo = filePath.toString();
                    String nombreArchivo = filePath.getFileName().toString();
                    String nombreArchivoSalida = construirNombreArchivoSalida(nombreArchivo);
                    System.out.println("Archivo encontrado: " + nombreArchivo);

                    String rutaDestino = construirRutaArchivoSalida(nombreArchivoSalida);
                    listaArchivos.add(new String[]{rutaArchivo, construirRutaArchivoTemporar(nombreArchivoSalida), rutaDestino, nombreArchivo, nombreArchivoSalida, "true"});
                });
        } catch (IOException e) {
            LOG.warn("IOException al obtener archivos:", e);
        }

        return listaArchivos;
    }    
    

    private String construirNombreArchivoSalida(String nombreArchivoEntrada) {
        return nombreArchivoEntrada.replaceAll("\\.mp4", ".mp4");
    }

    private String construirRutaArchivoSalida(String nombreArchivoSalida) {
        String fechaDirectorio = nombreArchivoSalida.substring(8, 16); // Extraer la fecha del nombre del archivo
        String rutaDestino = canal.getDestino() + File.separator + canal.getAlias();

        // Crear la ruta completa de la carpeta de la fecha en la ruta de destino
        String rutaFechaDestino = rutaDestino + File.separator + fechaDirectorio;

        // Verificar si la carpeta de la fecha ya existe en la ruta de destino
        File carpetaFechaDestino = new File(rutaFechaDestino);
        if (!carpetaFechaDestino.exists()) {
            // Si la carpeta no existe, intenta crearla
            if (carpetaFechaDestino.mkdirs()) {
                System.out.println("Carpeta creada en la ruta de destino: " + rutaFechaDestino);
            } else {
                System.err.println("Error al crear la carpeta en la ruta de destino: " + rutaFechaDestino);
                // Puedes manejar este error según sea necesario
            }
        }

        // Construir la ruta de salida completa para el archivo
        String rutaSalida = rutaFechaDestino + File.separator + nombreArchivoSalida;
        System.out.println("Ruta de salida: " + rutaSalida);
        return rutaSalida;
}

    private String construirRutaArchivoTemporar(String nombreArchivoSalida) {
        String rutaTemporar = Compressor.DIR_TMP + File.separator + nombreArchivoSalida;
        System.out.println("Ruta temporal: " + rutaTemporar);
        return rutaTemporar;
    }

    private long obtenerDuracionVideosEnRuta(String rutaOrigen) {
        File directorio = new File(rutaOrigen);
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
