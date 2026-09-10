package jpl.mipl.mars.mis;

import java.io.StringReader;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import javax.json.Json;
import javax.json.JsonObject;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PatternOptionBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Marsette Vona
 */
public class MISCLI {

    private static final Logger log = LoggerFactory.getLogger(MISCLI.class);

    public static void main(String[] args) {
        try {
            MISCLI instance = new MISCLI();
            instance.run(args);
        } catch (Exception ex) {
            System.err.println(ex.toString());
            System.exit(1);
        }
    }

    private void run(String[] args) {

        Options options = new Options();

        Option imageOption =
            new Option("i", "image", true, "source image s3:// or http[s]:// or file:// URL or local path");
        options.addOption(imageOption);

        Option lineOption = new Option("l", "line", true, "pixel line (row)");
        lineOption.setType(PatternOptionBuilder.NUMBER_VALUE);
        options.addOption(lineOption);

        Option sampleOption = new Option("s", "sample", true, "pixel sample (col)");
        sampleOption.setType(PatternOptionBuilder.NUMBER_VALUE);
        options.addOption(sampleOption);

        Option linesOption = new Option("ll", "lines", true, "comma separated pixel lines (rows)");
        options.addOption(linesOption);

        Option samplesOption = new Option("ss", "samples", true, "comma separated pixel samples (cols)");
        options.addOption(samplesOption);

        Option fileOption = new Option("f", "file", true,
                                       "read comma separate pixel line,sample pairs from text file, one pair per line");
        options.addOption(fileOption);

        Option originOption = new Option("o", "origin", true, "pixel line and sample origin, 0 or 1 (default 1)");
        originOption.setType(PatternOptionBuilder.NUMBER_VALUE);
        options.addOption(originOption);

        Option labelOption = new Option("a", "label", false, "label result");
        options.addOption(labelOption);

        Option batchOption = new Option("b", "batch", false, "always return batch result even for one pixel");
        options.addOption(batchOption);

        Option rdrOption = new Option("r", "rdr", true, "image type override");
        options.addOption(rdrOption);

        Option interpOption = new Option("p", "interp", true, "interpolation: none, interp_dn, interp_nonzero");
        options.addOption(interpOption);

        Option planesOption = new Option("pl", "planes", true, "comma separated planes nx,ny,nz,d,...");
        options.addOption(planesOption);

        Option spheresOption = new Option("sp", "spheres", true, "comma separated spheres cx,cy,cz,r,...");
        options.addOption(spheresOption);

        Option surfaceOption = new Option("sf", "surface", true, "surface plane nx,ny,nz,d");
        options.addOption(surfaceOption);

        Option profileOption = new Option("ap", "aws-profile", true, "override default AWS profile");
        options.addOption(profileOption);

        Option regionOption = new Option("ar", "aws-region", true, "override default AWS region");
        options.addOption(regionOption);

        Option awsHeaderOption = new Option("xaws", "x-aws", true,
                                            "use AWS credentials from base64 encoded X-AWS header string, " +
                                            "mutually exclusive with --aws-profile and --aws-region");
        options.addOption(awsHeaderOption);

        Option missionOption = new Option("m", "mission", true,
                                          "mission, one of " + String.join(",", ExplicitMISParams.SUPPORTED_MISSIONS) +
                                          ", defaults to " + ExplicitMISParams.DEFAULT_MISSION);
        options.addOption(missionOption);

        Option helpOption = new Option("h", "help", false, "display this help message and exit");
        options.addOption(helpOption);

        Option debugOption = new Option("d", "debug", false, "debug mode");
        options.addOption(debugOption);
        
        Option versionOption = new Option("v", "version", false, "show version");
        options.addOption(versionOption);

        Option quietOption = new Option("q", "quiet", false, "quiet mode");
        options.addOption(quietOption);

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine commandLine = parser.parse(options, args);
            
            if (commandLine.hasOption(helpOption.getOpt())) {
                new HelpFormatter().printHelp("MISCLI", options);
                return;
            }

            if (commandLine.hasOption(versionOption.getOpt())) {
                System.out.println("MISCLI" + MISTask.getVersion(MISCLI.class));
                return;
            }

            MISTask.quiet = commandLine.hasOption(quietOption.getOpt());
            MISTask.debug = !MISTask.quiet && commandLine.hasOption(debugOption.getOpt());
            if (MISTask.debug) {
                MISTask.setLogLevel(MISTask.log, "DEBUG");
                MISTask.setLogLevel(log, "DEBUG");
            }

            if (!commandLine.hasOption(imageOption.getOpt())) {
                throw new IllegalArgumentException("--image required");
            }

            String imgUrl = commandLine.getOptionValue(imageOption.getOpt());

            boolean hasLine = commandLine.hasOption(lineOption.getOpt());
            boolean hasLines = commandLine.hasOption(linesOption.getOpt());
            boolean hasSample = commandLine.hasOption(sampleOption.getOpt());
            boolean hasSamples = commandLine.hasOption(samplesOption.getOpt());
            boolean hasFile = commandLine.hasOption(fileOption.getOpt());

            if (((hasLine ? 1 : 0) + (hasLines ? 1 : 0) + (hasFile ? 1 : 0)) > 1) {
                throw new IllegalArgumentException("--line, --lines, and --file are mutually exclusive");
            }
            if (((hasSample ? 1 : 0) + (hasSamples ? 1 : 0) + (hasFile ? 1 : 0)) > 1) {
                throw new IllegalArgumentException("--sample, --samples, and --file are mutually exclusive");
            }

            float lines[] = null;
            if (hasLine) {
                lines = new float[] { ((Number)commandLine.getParsedOptionValue(lineOption.getOpt())).floatValue() };
            } else if (hasLines) {
                lines = MISParams.parseFloatArray(commandLine.getOptionValue(linesOption.getOpt()), "lines");
            }

            float samples[] = null;
            if (hasSample) {
                samples =
                    new float[] { ((Number)commandLine.getParsedOptionValue(sampleOption.getOpt())).floatValue() };
            } else if (hasSamples) {
                samples =
                    MISParams.parseFloatArray(commandLine.getOptionValue(samplesOption.getOpt()), "samples");
            }

            if (hasFile) {
                String file = commandLine.getOptionValue(fileOption.getOpt());
                if (!MISTask.quiet) {
                    log.info("reading comma separated line, sample pairs from file \"" + file + "\"");
                }
                try (var br = new BufferedReader(new FileReader(file))) {
                    var ll = new ArrayList<Float>();
                    var ss  = new ArrayList<Float>();
                    String line = br.readLine();
                    for (int n = 1; line != null; line = br.readLine(), n++) {
                        float[] vals = MISParams.parseFloatArray(line, "input line " + n);
                        if (vals.length != 2) {
                            throw new IllegalArgumentException("error parsing input line " + n +
                                                               ": not two comma separated numbers");
                        }
                        ll.add(vals[0]);
                        ss.add(vals[1]);
                    }
                    int nr = ll.size();
                    if (!MISTask.quiet) {
                        log.info("read " + nr + " comma separated line, sample pairs");
                    }
                    lines = new float[nr];
                    samples = new float[nr];
                    for (int i = 0; i < nr; i++) {
                        lines[i] = ll.get(i);
                        samples[i] = ss.get(i);
                    }
                }
            }

            Number originNumber = (Number)commandLine.getParsedOptionValue(originOption.getOpt());
            int origin = originNumber == null ? 1 : originNumber.intValue();
            
            boolean batch = commandLine.hasOption(batchOption.getOpt());

            boolean label = commandLine.hasOption(labelOption.getOpt());

            String rdrType = commandLine.getOptionValue(rdrOption.getOpt());

            SparseImage.InterpMode interp =
                MISParams.parseEnum(commandLine.getOptionValue(interpOption.getOpt()),
                                    "interp", SparseImage.InterpMode.class, SparseImage.InterpMode.none);

            double[][] planes = null;
            if (commandLine.hasOption(planesOption.getOpt())) {
                double[] t = MISParams.parseDoubleArray(commandLine.getOptionValue(planesOption.getOpt()), "planes");
                planes = VolumeMISParams.splitQuads(t, "planes");
            }

            double[][] spheres = null;
            if (commandLine.hasOption(spheresOption.getOpt())) {
                double[] t = MISParams.parseDoubleArray(commandLine.getOptionValue(spheresOption.getOpt()), "spheres");
                spheres = VolumeMISParams.splitQuads(t, "spheres");
            }

            double[] surface = null;
            if (commandLine.hasOption(surfaceOption.getOpt())) {
                surface = MISParams.parseDoubleArray(commandLine.getOptionValue(surfaceOption.getOpt()), "surface");
            }

            boolean isVolume = planes != null || spheres != null;
            if (isVolume) {
                if (lines != null || samples != null) {
                    throw new IllegalArgumentException
                        ("--file, --line[s], and --sample[s] cannot be combined with --planes or --spheres");
                }
            } else if (lines == null || samples == null) {
                throw new IllegalArgumentException
                    ("--file or --line[s] and --sample[s] required without --planes or --spheres"); 
            }

            S3Helper s3 = null;
            boolean useAwsHeader = commandLine.hasOption(awsHeaderOption.getOpt());
            if (imgUrl.toLowerCase().startsWith("s3://")) {
                if (useAwsHeader) {
                    String hdr = commandLine.getOptionValue(awsHeaderOption.getOpt());
                    String headerJsonStr = new String(Base64.decodeBase64(hdr), "UTF-8");
                    JsonObject awsSessionConfig = Json.createReader(new StringReader(headerJsonStr)).readObject();
                    s3 = new S3Helper(awsSessionConfig);
                } else {
                    String awsProfile = commandLine.getOptionValue(profileOption.getOpt());
                    String awsRegion = commandLine.getOptionValue(regionOption.getOpt());
                    s3 = new S3Helper(awsProfile, awsRegion); //null ok
                }
            }

            String mission = commandLine.getOptionValue(missionOption.getOpt());
            if (mission == null) {
                mission = ExplicitMISParams.DEFAULT_MISSION;
            }
            
            if (!MISTask.quiet) {
                Runtime rt = Runtime.getRuntime();
                log.info("image sampler version " + MISTask.getVersion());
                log.info(rt.availableProcessors() + " cores, " + (int)(rt.maxMemory() * 1e-6) + "MB");
                if (s3 != null) {
                    log.info((useAwsHeader ? "using X-AWS header" : ("S3 AWS profile: " + s3.getAWSProfile())) +
                             ", region: " + s3.getAWSRegion());
                }
                log.info("mission: " + mission);
            }

            try {
                var params = isVolume ?
                    new VolumeMISParams(imgUrl, planes, spheres, surface) :
                    new ExplicitMISParams(imgUrl, lines, samples, origin, batch, label, rdrType, interp, mission);

                var task = new MISTask(params, s3);

                if (task.isStreaming()) {
                    task.run(System.out);
                } else {
                    System.out.println(task.run());
                }
                
            } catch (Exception ex) {
                log.error("error processing " + imgUrl, ex);
                System.exit(1);
            }

        } catch (Exception ex) {
            log.error("MISCLI error", ex);
            System.exit(1);
        }
    }
}
