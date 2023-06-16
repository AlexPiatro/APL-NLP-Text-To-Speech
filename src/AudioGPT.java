import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

import com.sun.speech.freetts.audio.AudioPlayer;
import com.sun.speech.freetts.audio.SingleFileAudioPlayer;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

import javax.sound.sampled.AudioFileFormat;

public class AudioGPT {
    private static final Pattern WORD_PATTERN = Pattern.compile("^[a-zA-Z]+$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[0-9]+$");
    private static final String VOICE_NAME = "kevin16";
    public static void main(String[] args) {
        String filename = "Readable.txt";
        List<String> lexemeBuffer = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;

            while ((line = reader.readLine()) != null) {
                processLine(line, lexemeBuffer);
            }

            System.out.println("End of file reached.");
            // Perform further analysis using the lexemeBuffer
            for (String lexeme : lexemeBuffer) {
                // Do something with the lexeme
                System.out.println("Lexeme: " + lexeme);
            }

            // Perform syntax analysis using Stanford CoreNLP
            analyzeSyntax(lexemeBuffer);
        } catch (FileNotFoundException e) {
            System.out.println("File not found: " + filename);
        } catch (IOException e) {
            System.out.println("Error reading the file: " + e.getMessage());
        }
    }

    private static void processLine(String line, List<String> lexemeBuffer) {
        Scanner scanner = new Scanner(line);

        // Set delimiter to match words and punctuation
        scanner.useDelimiter("(?i)[\\s-']+|(?<=\\p{Punct})(?=\\S)|(?<=\\S)(?=\\p{Punct})");

        while (scanner.hasNext()) {
            String lexeme = scanner.next();
            processLexeme(lexeme, lexemeBuffer);
        }

        scanner.close();
    }

    private static void processLexeme(String lexeme, List<String> lexemeBuffer) {
        // Remove leading and trailing punctuation
        String trimmedLexeme = lexeme.replaceAll("^[^a-zA-Z0-9']+|[^a-zA-Z0-9']+$", "");

        if (isWord(trimmedLexeme)) {
            lexemeBuffer.add(trimmedLexeme);
        } else if (isNumber(trimmedLexeme)) {
            lexemeBuffer.add(trimmedLexeme);
        } else {
            // Store special lexemes in the buffer as well
            lexemeBuffer.add(lexeme);
        }
    }

    private static boolean isWord(String lexeme) {
        return lexeme.matches("^[a-zA-Z]+$");
    }

    private static boolean isNumber(String lexeme) {
        return lexeme.matches("^[0-9]+$");
    }

    private static void analyzeSyntax(List<String> lexemeBuffer) {
        // Combine lexemes into a single text
        StringBuilder text = new StringBuilder();
        for (String lexeme : lexemeBuffer) {
            text.append(lexeme).append(" ");
        }

        // Use Stanford CoreNLP for syntax analysis
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        Annotation document = new Annotation(text.toString().trim());
        pipeline.annotate(document);

        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
        StringBuilder speechText = new StringBuilder(); // To store the combined text

        for (int i = 0; i < sentences.size(); i++) {
            CoreMap sentence = sentences.get(i);
            System.out.println("Sentence: " + sentence.get(CoreAnnotations.TextAnnotation.class));
            System.out.println("Syntax Tree: " + sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));

            // Access named entities
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);

            // Save POS tags to a file
            savePOSTags(tokens, "pos_tags_" + (i + 1) + ".txt");

            // Save named entity tags to a file
            saveNamedEntityTags(tokens, "named_entity_tags_" + (i + 1) + ".txt");

            // Save the dependency parse to a file
            SemanticGraph dependencies = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
            boolean saveSuccessful = saveDependencyParse(dependencies, "dependency_parse_" + (i + 1) + ".txt");

            if (saveSuccessful) {
                System.out.println("Dependency parse saved successfully.");
            } else {
                System.out.println("Failed to save dependency parse.");
            }

            // Append the text of each sentence
            speechText.append(sentence.get(CoreAnnotations.TextAnnotation.class)).append(" ");
        }

        // Text-to-speech conversion
        convertTextToSpeech(speechText.toString());
    }

    private static void savePOSTags(List<CoreLabel> tokens, String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (CoreLabel token : tokens) {
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                String posTag = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                writer.write(word + "\t" + posTag + "\n");
            }
        } catch (IOException e) {
            System.out.println("Error saving POS tags: " + e.getMessage());
        }
    }

    private static void saveNamedEntityTags(List<CoreLabel> tokens, String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            for (CoreLabel token : tokens) {
                String word = token.get(CoreAnnotations.TextAnnotation.class);
                String namedEntityTag = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
                writer.write(word + "\t" + namedEntityTag + "\n");
            }
        } catch (IOException e) {
            System.out.println("Error saving named entity tags: " + e.getMessage());
        }
    }

    private static boolean saveDependencyParse(SemanticGraph dependencies, String filename) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write(dependencies.toString());
            return true;
        } catch (IOException e) {
            System.out.println("Error saving dependency parse: " + e.getMessage());
            return false;
        }
    }

    private static void convertTextToSpeech(String text) {
        System.setProperty("freetts.voices", "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory");
        Voice voice;
        VoiceManager voiceManager = VoiceManager.getInstance();

        voice = voiceManager.getVoice(VOICE_NAME);

        if (voice != null) {
            voice.allocate();
            AudioPlayer aplayer = new SingleFileAudioPlayer("TextToSpeechAudioRecording.wav", AudioFileFormat.Type.WAVE);
            voice.setAudioPlayer(aplayer);

            // Split the text into sentences
            String[] sentences = text.split("(?<=[.!?])\\s+");

            // Iterate over each sentence
            for (String sentence : sentences) {
                // Pronounce the sentence with a pause at the end
                voice.speak(sentence);
                pause(300);  // Pause duration after each sentence (adjust as needed)
            }

            aplayer.close();
            voice.deallocate();
        } else {
            System.out.println("Cannot find the specified voice: " + VOICE_NAME);
        }
    }

    private static void pause(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
