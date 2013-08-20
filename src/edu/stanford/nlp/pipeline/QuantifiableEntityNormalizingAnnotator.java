package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.QuantifiableEntityNormalizer;
import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NormalizedNamedEntityTagAnnotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.Timing;

import java.util.*;

/**
 * This class provides a facility for normalizing content of numerical named
 * entities (number, money, date, time) in the pipeline package world. It uses a
 * lot of code with {@link edu.stanford.nlp.ie.QuantifiableEntityNormalizer}.
 * New stuff should generally be added there so as to reduce code duplication.
 * 
 * @author Jenny Finkel
 * @author Christopher Manning (extended for RTE)
 * @author Chris Cox (original version)
 */

public class QuantifiableEntityNormalizingAnnotator implements Annotator {

  private Timing timer = new Timing();
  private final boolean VERBOSE;
  private static final String DEFAULT_BACKGROUND_SYMBOL = "O";
  private final boolean collapse;

  public static final String BACKGROUND_SYMBOL_PROPERTY = "background";
  public static final String COLLAPSE_PROPERTY = "collapse";

  public QuantifiableEntityNormalizingAnnotator() {
    this(DEFAULT_BACKGROUND_SYMBOL, true);
  }

  public QuantifiableEntityNormalizingAnnotator(boolean verbose) {
    this(DEFAULT_BACKGROUND_SYMBOL, verbose);
  }

  public QuantifiableEntityNormalizingAnnotator(String name, Properties props) {
    String property = name + "." + BACKGROUND_SYMBOL_PROPERTY;
    String backgroundSymbol = props.getProperty(property, DEFAULT_BACKGROUND_SYMBOL);
    // this next line is yuck as QuantifiableEntityNormalizer is still static
    QuantifiableEntityNormalizer.BACKGROUND_SYMBOL = backgroundSymbol;
    property = name + "." + COLLAPSE_PROPERTY;
    collapse = PropertiesUtils.getBool(props, property, true);
    VERBOSE = false;
  }

  /**
   * Do quantity entity normalization and collapse together multitoken quantity
   * entities into a single token.
   * 
   * @param backgroundSymbol
   *          NER background symbol
   * @param verbose
   *          Whether to write messages
   */
  public QuantifiableEntityNormalizingAnnotator(String backgroundSymbol, boolean verbose) {
    this(backgroundSymbol, verbose, true);
  }

  /**
   * Do quantity entity normalization and collapse together multitoken quantity
   * entities into a single token.
   * 
   * @param verbose
   *          Whether to write messages
   * @param collapse
   *          Whether to collapse multitoken quantity entities.
   */
  public QuantifiableEntityNormalizingAnnotator(boolean verbose, boolean collapse) {
    this(DEFAULT_BACKGROUND_SYMBOL, verbose, collapse);
  }

  public QuantifiableEntityNormalizingAnnotator(String backgroundSymbol, boolean verbose, boolean collapse) {
    // this next line is yuck as QuantifiableEntityNormalizer is still static
    QuantifiableEntityNormalizer.BACKGROUND_SYMBOL = backgroundSymbol;
    VERBOSE = verbose;
    this.collapse = collapse;
  }

  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      timer.start();
      System.err.print("Normalizing quantifiable entities...");
    }
    if (annotation.containsKey(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
      for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
        List<CoreLabel> words = new ArrayList<CoreLabel>();
        for (CoreLabel token : tokens) {
          CoreLabel word = new CoreLabel();
          word.setWord(token.word());
          word.setNER(token.ner());
          word.setTag(token.tag());
          
          // copy fields potentially set by SUTime
          NumberSequenceClassifier.transferAnnotations(token, word);
          
          words.add(word);
        }
        doOneSentence(words);
        for (int i = 0; i < words.size(); i++) {
          String ner = words.get(i).ner();
          tokens.get(i).setNER(ner);
          tokens.get(i).set(NormalizedNamedEntityTagAnnotation.class,
              words.get(i).get(NormalizedNamedEntityTagAnnotation.class));
        }
      }
      if (VERBOSE) {
        timer.stop("done.");
        System.err.println("output: " + sentences + '\n');
      }
    } else if (annotation.containsKey(CoreAnnotations.TokensAnnotation.class)) {
      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
      List<CoreLabel> words = new ArrayList<CoreLabel>();
      for (CoreLabel token : tokens) {
        CoreLabel word = new CoreLabel();
        word.setWord(token.word());
        word.setNER(token.ner());
        word.setTag(token.tag());
        
        // copy fields potentially set by SUTime
        NumberSequenceClassifier.transferAnnotations(token, word);
        
        words.add(word);
      }
      doOneSentence(words);
      for (int i = 0; i < words.size(); i++) {
        String ner = words.get(i).ner();
        tokens.get(i).setNER(ner);
        tokens.get(i).set(NormalizedNamedEntityTagAnnotation.class,
            words.get(i).get(NormalizedNamedEntityTagAnnotation.class));
      }
    } else {
      throw new RuntimeException("unable to find sentences in: " + annotation);
    }
  }

  private <TOKEN extends CoreLabel> void doOneSentence(List<TOKEN> words) {
    QuantifiableEntityNormalizer.addNormalizedQuantitiesToEntities(words, collapse);
  }

}
