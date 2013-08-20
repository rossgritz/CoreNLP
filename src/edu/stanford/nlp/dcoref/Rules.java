package edu.stanford.nlp.dcoref;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.dcoref.Dictionaries.Animacy;
import edu.stanford.nlp.dcoref.Dictionaries.Gender;
import edu.stanford.nlp.dcoref.Dictionaries.MentionType;
import edu.stanford.nlp.dcoref.Dictionaries.Number;
import edu.stanford.nlp.dcoref.Dictionaries.Person;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SpeakerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.UtteranceAnnotation;
import edu.stanford.nlp.math.NumberMatchingRegex;
import edu.stanford.nlp.util.Pair;


/**
 * Rules for coref system (mention detection, entity coref, event coref)
 * The name of the method for mention detection starts with detection,
 * for entity coref starts with entity, and for event coref starts with event.
 * 
 * @author heeyoung
 */
public class Rules {

  private static final boolean DEBUG = true;

  public static boolean entityBothHaveProper(CorefCluster mentionCluster,
      CorefCluster potentialAntecedent) {
    boolean mentionClusterHaveProper = false;
    boolean potentialAntecedentHaveProper = false;
    
    for (Mention m : mentionCluster.corefMentions) {
      if (m.mentionType==MentionType.PROPER) {
        mentionClusterHaveProper = true;
      }
    }
    for (Mention a : potentialAntecedent.corefMentions) {
      if (a.mentionType==MentionType.PROPER) {
        potentialAntecedentHaveProper = true;
      }
    }
    return (mentionClusterHaveProper && potentialAntecedentHaveProper);
  }
  public static boolean entitySameProperHeadLastWord(CorefCluster mentionCluster,
      CorefCluster potentialAntecedent, Mention mention, Mention ant) {
    for (Mention m : mentionCluster.getCorefMentions()){
      for (Mention a : potentialAntecedent.getCorefMentions()) {
        if (entitySameProperHeadLastWord(m, a)) return true;
      }
    }
    return false;
  }
  
  public static boolean entityAlias(CorefCluster mentionCluster, CorefCluster potentialAntecedent,
      Semantics semantics, Dictionaries dict) throws Exception {
    
    Mention mention = mentionCluster.getRepresentativeMention();
    Mention antecedent = potentialAntecedent.getRepresentativeMention();
    if(mention.mentionType!=MentionType.PROPER
        || antecedent.mentionType!=MentionType.PROPER) return false;
    
    Method meth = semantics.wordnet.getClass().getMethod("alias", new Class[]{Mention.class, Mention.class});
    if((Boolean) meth.invoke(semantics.wordnet, new Object[]{mention, antecedent})) {
      return true;
    }
    return false;
  }
  public static boolean entityIWithinI(CorefCluster mentionCluster,
      CorefCluster potentialAntecedent, Dictionaries dict) {
    for(Mention m : mentionCluster.getCorefMentions()) {
      for(Mention a : potentialAntecedent.getCorefMentions()) {
        if(entityIWithinI(m, a, dict)) return true;
      }
    }
    return false;
  }
  public static boolean entityPersonDisagree(Document document, CorefCluster mentionCluster, CorefCluster potentialAntecedent, Dictionaries dict){
    boolean disagree = false;
    for(Mention m : mentionCluster.getCorefMentions()) {
      for(Mention ant : potentialAntecedent.getCorefMentions()) {
        if(entityPersonDisagree(document, m, ant, dict)) {
          disagree = true;        
        }
      }
    }
    if(disagree) return true;
    else return false;
  }
  /** Word inclusion except stop words  */
  public static boolean entityWordsIncluded(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention mention, Mention ant) {
    Set<String> wordsExceptStopWords = new HashSet<String>(mentionCluster.words);
    wordsExceptStopWords.removeAll(Arrays.asList(new String[]{ "the","this", "mr.", "miss", "mrs.", "dr.", "ms.", "inc.", "ltd.", "corp.", "'s"}));
    wordsExceptStopWords.remove(mention.headString.toLowerCase());
    if(potentialAntecedent.words.containsAll(wordsExceptStopWords)) return true;
    else return false;
  }

  /** Compatible modifier only  */
  public static boolean entityHaveIncompatibleModifier(CorefCluster mentionCluster, CorefCluster potentialAntecedent) {
    for(Mention m : mentionCluster.corefMentions){
      for(Mention ant : potentialAntecedent.corefMentions){
        if(entityHaveIncompatibleModifier(m, ant)) return true;
      }
    }
    return false;
  }
  public static boolean entityIsRoleAppositive(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention m1, Mention m2, Dictionaries dict) {
    if(!entityAttributesAgree(mentionCluster, potentialAntecedent)) return false;
    return m1.isRoleAppositive(m2, dict) || m2.isRoleAppositive(m1, dict);
  }
  public static boolean entityIsRelativePronoun(Mention m1, Mention m2) {
      return m1.isRelativePronoun(m2) || m2.isRelativePronoun(m1);
  }

  public static boolean entityIsAcronym(CorefCluster mentionCluster, CorefCluster potentialAntecedent) {
    for(Mention m : mentionCluster.corefMentions){
      if(m.isPronominal()) continue;
      for(Mention ant : potentialAntecedent.corefMentions){
        if(m.isAcronym(ant) || ant.isAcronym(m)) return true;
      }
    }
    return false;
  }

  public static boolean entityIsPredicateNominatives(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention m1, Mention m2) {
    if(!entityAttributesAgree(mentionCluster, potentialAntecedent)) return false;
    if ((m1.startIndex <= m2.startIndex && m1.endIndex >= m2.endIndex)
            || (m1.startIndex >= m2.startIndex && m1.endIndex <= m2.endIndex)) {
      return false;
    }
    return m1.isPredicateNominatives(m2) || m2.isPredicateNominatives(m1);
  }

  public static boolean entityIsApposition(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention m1, Mention m2) {
    if(!entityAttributesAgree(mentionCluster, potentialAntecedent)) return false;
    if(m1.mentionType==MentionType.PROPER && m2.mentionType==MentionType.PROPER) return false;
    if(m1.nerString.equals("LOCATION")) return false;
    return m1.isApposition(m2) || m2.isApposition(m1);
  }

  public static boolean entityAttributesAgree(CorefCluster mentionCluster, CorefCluster potentialAntecedent){
    
    boolean hasExtraAnt = false;
    boolean hasExtraThis = false;

    // number
    if(!mentionCluster.numbers.contains(Number.UNKNOWN)){
      for(Number n : potentialAntecedent.numbers){
        if(n!=Number.UNKNOWN && !mentionCluster.numbers.contains(n)) hasExtraAnt = true;
      }
    }
    if(!potentialAntecedent.numbers.contains(Number.UNKNOWN)){
      for(Number n : mentionCluster.numbers){
        if(n!=Number.UNKNOWN && !potentialAntecedent.numbers.contains(n)) hasExtraThis = true;
      }
    }

    if(hasExtraAnt && hasExtraThis) return false;

    // gender
    hasExtraAnt = false;
    hasExtraThis = false;

    if(!mentionCluster.genders.contains(Gender.UNKNOWN)){
      for(Gender g : potentialAntecedent.genders){
        if(g!=Gender.UNKNOWN && !mentionCluster.genders.contains(g)) hasExtraAnt = true;
      }
    }
    if(!potentialAntecedent.genders.contains(Gender.UNKNOWN)){
      for(Gender g : mentionCluster.genders){
        if(g!=Gender.UNKNOWN && !potentialAntecedent.genders.contains(g)) hasExtraThis = true;
      }
    }
    if(hasExtraAnt && hasExtraThis) return false;

    // animacy
    hasExtraAnt = false;
    hasExtraThis = false;

    if(!mentionCluster.animacies.contains(Animacy.UNKNOWN)){
      for(Animacy a : potentialAntecedent.animacies){
        if(a!=Animacy.UNKNOWN && !mentionCluster.animacies.contains(a)) hasExtraAnt = true;
      }
    }
    if(!potentialAntecedent.animacies.contains(Animacy.UNKNOWN)){
      for(Animacy a : mentionCluster.animacies){
        if(a!=Animacy.UNKNOWN && !potentialAntecedent.animacies.contains(a)) hasExtraThis = true;
      }
    }
    if(hasExtraAnt && hasExtraThis) return false;

    // NE type
    hasExtraAnt = false;
    hasExtraThis = false;

    if(!mentionCluster.nerStrings.contains("O") && !mentionCluster.nerStrings.contains("MISC")){
      for(String ne : potentialAntecedent.nerStrings){
        if(!ne.equals("O") && !ne.equals("MISC") && !mentionCluster.nerStrings.contains(ne)) hasExtraAnt = true;
      }
    }
    if(!potentialAntecedent.nerStrings.contains("O") && !potentialAntecedent.nerStrings.contains("MISC")){
      for(String ne : mentionCluster.nerStrings){
        if(!ne.equals("O") && !ne.equals("MISC") && !potentialAntecedent.nerStrings.contains(ne)) hasExtraThis = true;
      }
    }
    return ! (hasExtraAnt && hasExtraThis);
  }

  public static boolean entityRelaxedHeadsAgreeBetweenMentions(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention m, Mention ant) {
    if(m.isPronominal() || ant.isPronominal()) return false;
    if(m.headsAgree(ant)) return true;
    return false;
  }

  public static boolean entityHeadsAgree(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Mention m, Mention ant, Dictionaries dict) {
    boolean headAgree = false;
    if(m.isPronominal() || ant.isPronominal()
        || dict.allPronouns.contains(m.spanToString().toLowerCase())
        || dict.allPronouns.contains(ant.spanToString().toLowerCase())) return false;
    for(Mention a : potentialAntecedent.corefMentions){
      if(a.headString.equals(m.headString)) headAgree= true;
    }
    return headAgree;
  }

  public static boolean entityExactStringMatch(CorefCluster mentionCluster, CorefCluster potentialAntecedent, Dictionaries dict, Set<Mention> roleSet){
    boolean matched = false;
    for(Mention m : mentionCluster.corefMentions){
      if(roleSet.contains(m)) return false;
      if(m.isPronominal()) {
        continue;
      }
      String mSpan = m.spanToString().toLowerCase();
      if(dict.allPronouns.contains(mSpan)) {
        continue;
      }
      for(Mention ant : potentialAntecedent.corefMentions){
        if(ant.isPronominal()) {
          continue;
        }
        String antSpan = ant.spanToString().toLowerCase();
        if(dict.allPronouns.contains(antSpan)) continue;
        if(mSpan.equals(antSpan)) matched = true;
        if(mSpan.equals(antSpan+" 's") || antSpan.equals(mSpan+" 's")) matched = true;
      }
    }
    return matched;
  }

  /**
   * Exact string match except phrase after head (only for proper noun):
   * For dealing with a error like "[Mr. Bickford] <- [Mr. Bickford , an 18-year mediation veteran]"
   */
  public static boolean entityRelaxedExactStringMatch(
      CorefCluster mentionCluster,
      CorefCluster potentialAntecedent,
      Mention mention,
      Mention ant,
      Dictionaries dict,
      Set<Mention> roleSet){
    if(roleSet.contains(mention)) return false;
    if(mention.isPronominal() || ant.isPronominal()
        || dict.allPronouns.contains(mention.spanToString().toLowerCase())
        || dict.allPronouns.contains(ant.spanToString().toLowerCase())) return false;
    String mentionSpan = mention.removePhraseAfterHead();
    String antSpan = ant.removePhraseAfterHead();
    if(mentionSpan.equals("") || antSpan.equals("")) return false;

    if(mentionSpan.equals(antSpan) || mentionSpan.equals(antSpan+" 's") || antSpan.equals(mentionSpan+" 's")){
      return true;
    }
    return false;
  }

  /** Check whether two mentions are in i-within-i relation (Chomsky, 1981) */
  public static boolean entityIWithinI(Mention m1, Mention m2, Dictionaries dict){
    // check for nesting: i-within-i
    if(!m1.isApposition(m2) && !m2.isApposition(m1)
        && !m1.isRelativePronoun(m2) && !m2.isRelativePronoun(m1)
        && !m1.isRoleAppositive(m2, dict) && !m2.isRoleAppositive(m1, dict)
    ){
      if(m1.includedIn(m2) || m2.includedIn(m1)){
        return true;
      }
    }
    return false;
  }
  

  /** Check whether later mention has incompatible modifier */
  public static boolean entityHaveIncompatibleModifier(Mention m, Mention ant) {
    if(!ant.headString.equalsIgnoreCase(m.headString)) return false;   // only apply to same head mentions
    boolean thisHasExtra = false;
    int lengthThis = m.originalSpan.size();
    int lengthM = ant.originalSpan.size();
    Set<String> thisWordSet = new HashSet<String>();
    Set<String> antWordSet = new HashSet<String>();
    Set<String> locationModifier = new HashSet<String>(Arrays.asList("east", "west", "north", "south",
        "eastern", "western", "northern", "southern", "upper", "lower"));

    for (int i=0; i< lengthThis ; i++){
      String w1 = m.originalSpan.get(i).get(TextAnnotation.class).toLowerCase();
      String pos1 = m.originalSpan.get(i).get(PartOfSpeechAnnotation.class);
      if (!(pos1.startsWith("N") || pos1.startsWith("JJ") || pos1.equals("CD")
            || pos1.startsWith("V")) || w1.equalsIgnoreCase(m.headString)) {
        continue;
      }
      thisWordSet.add(w1);
    }
    for (int j=0 ; j < lengthM ; j++){
      String w2 = ant.originalSpan.get(j).get(TextAnnotation.class).toLowerCase();
      antWordSet.add(w2);
    }
    for (String w : thisWordSet){
      if(!antWordSet.contains(w)) thisHasExtra = true;
    }
    boolean hasLocationModifier = false;
    for(String l : locationModifier){
      if(antWordSet.contains(l) && !thisWordSet.contains(l)) {
        hasLocationModifier = true;
      }
    }
    return (thisHasExtra || hasLocationModifier);
  }
  /** Check whether two mentions have different locations */
  public static boolean entityHaveDifferentLocation(Mention m, Mention a, Dictionaries dict) {

    // state and country cannot be coref
    if ((dict.statesAbbreviation.containsKey(a.spanToString()) || dict.statesAbbreviation.containsValue(a.spanToString()))
          && (m.headString.equalsIgnoreCase("country") || m.headString.equalsIgnoreCase("nation"))) {
      return true;
    }

    Set<String> locationM = new HashSet<String>();
    Set<String> locationA = new HashSet<String>();
    String mString = m.spanToString().toLowerCase();
    String aString = a.spanToString().toLowerCase();
    Set<String> locationModifier = new HashSet<String>(Arrays.asList("east", "west", "north", "south",
        "eastern", "western", "northern", "southern", "northwestern", "southwestern", "northeastern",
        "southeastern", "upper", "lower"));

    for (CoreLabel w : m.originalSpan){
      if (locationModifier.contains(w.get(TextAnnotation.class).toLowerCase())) return true;
      if (w.get(NamedEntityTagAnnotation.class).equals("LOCATION")) {
        String loc = w.get(TextAnnotation.class);
        if(dict.statesAbbreviation.containsKey(loc)) loc = dict.statesAbbreviation.get(loc);
        locationM.add(loc);
      }
    }
    for (CoreLabel w : a.originalSpan){
      if (locationModifier.contains(w.get(TextAnnotation.class).toLowerCase())) return true;
      if (w.get(NamedEntityTagAnnotation.class).equals("LOCATION")) {
        String loc = w.get(TextAnnotation.class);
        if(dict.statesAbbreviation.containsKey(loc)) loc = dict.statesAbbreviation.get(loc);
        locationA.add(loc);
      }
    }
    boolean mHasExtra = false;
    boolean aHasExtra = false;
    for (String s : locationM) {
      if (!aString.contains(s.toLowerCase())) mHasExtra = true;
    }
    for (String s : locationA) {
      if (!mString.contains(s.toLowerCase())) aHasExtra = true;
    }
    if(mHasExtra && aHasExtra) {
      return true;
    }
    return false;
  }

  /** Check whether two mentions have the same proper head words */
  public static boolean entitySameProperHeadLastWord(Mention m, Mention a) {
    if(!m.headString.equalsIgnoreCase(a.headString)
        || !m.sentenceWords.get(m.headIndex).get(PartOfSpeechAnnotation.class).startsWith("NNP")
        || !a.sentenceWords.get(a.headIndex).get(PartOfSpeechAnnotation.class).startsWith("NNP")) {
      return false;
    }
    if(!m.removePhraseAfterHead().toLowerCase().endsWith(m.headString)
        || !a.removePhraseAfterHead().toLowerCase().endsWith(a.headString)) {
      return false;
    }
    Set<String> mProperNouns = new HashSet<String>();
    Set<String> aProperNouns = new HashSet<String>();
    for (CoreLabel w : m.sentenceWords.subList(m.startIndex, m.headIndex)){
      if (w.get(PartOfSpeechAnnotation.class).startsWith("NNP")) {
        mProperNouns.add(w.get(TextAnnotation.class));
      }
    }
    for (CoreLabel w : a.sentenceWords.subList(a.startIndex, a.headIndex)){
      if (w.get(PartOfSpeechAnnotation.class).startsWith("NNP")) {
        aProperNouns.add(w.get(TextAnnotation.class));
      }
    }
    boolean mHasExtra = false;
    boolean aHasExtra = false;
    for (String s : mProperNouns) {
      if (!aProperNouns.contains(s)) mHasExtra = true;
    }
    for (String s : aProperNouns) {
      if (!mProperNouns.contains(s)) aHasExtra = true;
    }
    if(mHasExtra && aHasExtra) return false;
    return true;
  }

  static final HashSet<String> NUMBERS = new HashSet<String>(Arrays.asList(new String[]{"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "hundred", "thousand", "million", "billion"}));

  /** Check whether there is a new number in later mention */
  public static boolean entityNumberInLaterMention(Mention mention, Mention ant) {
    Set<String> antecedentWords = new HashSet<String>();
    for (CoreLabel w : ant.originalSpan){
      antecedentWords.add(w.get(TextAnnotation.class));
    }
    for (CoreLabel w : mention.originalSpan) {
      String word = w.get(TextAnnotation.class);
      // Note: this is locale specific for English and ascii numerals
      if (NumberMatchingRegex.isDouble(word)) {
        if (!antecedentWords.contains(word)) return true;
      } else {
        if (NUMBERS.contains(word.toLowerCase()) && !antecedentWords.contains(word)) return true;
      }
    }
    return false;
  }

  /** Have extra proper noun except strings involved in semantic match */
  public static boolean entityHaveExtraProperNoun(Mention m, Mention a, Set<String> exceptWords) {
    Set<String> mProper = new HashSet<String>();
    Set<String> aProper = new HashSet<String>();
    String mString = m.spanToString();
    String aString = a.spanToString();

    for (CoreLabel w : m.originalSpan){
      if (w.get(PartOfSpeechAnnotation.class).startsWith("NNP")) {
        mProper.add(w.get(TextAnnotation.class));
      }
    }
    for (CoreLabel w : a.originalSpan){
      if (w.get(PartOfSpeechAnnotation.class).startsWith("NNP")) {
        aProper.add(w.get(TextAnnotation.class));
      }
    }
    boolean mHasExtra = false;
    boolean aHasExtra = false;


    for (String s : mProper) {
      if (!aString.contains(s) && !exceptWords.contains(s.toLowerCase())) mHasExtra = true;
    }
    for (String s : aProper) {
      if (!mString.contains(s) && !exceptWords.contains(s.toLowerCase())) aHasExtra = true;
    }

    if(mHasExtra && aHasExtra) {
      return true;
    }
    return false;
  }

  public static boolean entityIsSpeaker(Document document,
      Mention mention, Mention ant, Dictionaries dict) {
    if(document.speakerPairs.contains(new Pair<Integer, Integer>(mention.mentionID, ant.mentionID))
        || document.speakerPairs.contains(new Pair<Integer, Integer>(ant.mentionID, mention.mentionID))) {
      return true;
    }

    if(mention.headWord.containsKey(SpeakerAnnotation.class)){
      for(String s : mention.headWord.get(SpeakerAnnotation.class).split(" ")) {
        if(ant.headString.equalsIgnoreCase(s)) return true;
      }
    }
    if(ant.headWord.containsKey(SpeakerAnnotation.class)){
      for(String s : ant.headWord.get(SpeakerAnnotation.class).split(" ")) {
        if(mention.headString.equalsIgnoreCase(s)) return true;
      }
    }
    return false;
  }

  public static boolean entityPersonDisagree(Document document, Mention m, Mention ant, Dictionaries dict) {
    boolean sameSpeaker = entitySameSpeaker(document, m, ant);

    if(sameSpeaker && m.person!=ant.person) {
      if ((m.person == Person.IT && ant.person == Person.THEY)
           || (m.person == Person.THEY && ant.person == Person.IT) || (m.person == Person.THEY && ant.person == Person.THEY)) {
        return false;
      } else if (m.person != Person.UNKNOWN && ant.person != Person.UNKNOWN)
        return true;
    }
    if(sameSpeaker) {
      if(!ant.isPronominal()) {
        if(m.person==Person.I || m.person==Person.WE || m.person==Person.YOU) return true;
      } else if(!m.isPronominal()) {
        if(ant.person==Person.I || ant.person==Person.WE || ant.person==Person.YOU) return true;
      }
    }
    if(m.person==Person.YOU && ant.appearEarlierThan(m)) {
      int mUtter = m.headWord.get(UtteranceAnnotation.class);
      if (document.speakers.containsKey(mUtter - 1)) {
        String previousSpeaker = document.speakers.get(mUtter - 1);
        int previousSpeakerID;
        try {
          previousSpeakerID = Integer.parseInt(previousSpeaker);
        } catch (Exception e) {
          return true;
        }
        if (ant.corefClusterID != document.allPredictedMentions.get(previousSpeakerID).corefClusterID && ant.person != Person.I) {
          return true;
        }
      } else {
        return true;
      }
    } else if (ant.person==Person.YOU && m.appearEarlierThan(ant)) {
      int aUtter = ant.headWord.get(UtteranceAnnotation.class);
      if (document.speakers.containsKey(aUtter - 1)) {
        String previousSpeaker = document.speakers.get(aUtter - 1);
        int previousSpeakerID;
        try {
          previousSpeakerID = Integer.parseInt(previousSpeaker);
        } catch (Exception e) {
          return true;
        }
        if (m.corefClusterID != document.allPredictedMentions.get(previousSpeakerID).corefClusterID && m.person != Person.I) {
          return true;
        }
      } else {
        return true;
      }
    }
    return false;
  }

  public static boolean entitySameSpeaker(Document document, Mention m, Mention ant) {
    if(m.headWord.containsKey(SpeakerAnnotation.class) == false ||
        ant.headWord.containsKey(SpeakerAnnotation.class) == false){
      return false;
    }

    int mSpeakerID;
    int antSpeakerID;
    String mSpeakerStr = m.headWord.get(SpeakerAnnotation.class);
    String antSpeakerStr = ant.headWord.get(SpeakerAnnotation.class);
    if (NumberMatchingRegex.isDecimalInteger(mSpeakerStr) && NumberMatchingRegex.isDecimalInteger(antSpeakerStr)) {
      try {
        mSpeakerID = Integer.parseInt(mSpeakerStr);
        antSpeakerID = Integer.parseInt(ant.headWord.get(SpeakerAnnotation.class));
      } catch (Exception e) {
        return (m.headWord.get(SpeakerAnnotation.class).equals(ant.headWord.get(SpeakerAnnotation.class)));
      }
    } else {
      return (m.headWord.get(SpeakerAnnotation.class).equals(ant.headWord.get(SpeakerAnnotation.class)));
    }
    int mSpeakerClusterID = document.allPredictedMentions.get(mSpeakerID).corefClusterID;
    int antSpeakerClusterID = document.allPredictedMentions.get(antSpeakerID).corefClusterID;
    return (mSpeakerClusterID==antSpeakerClusterID);
  }

  public static boolean entitySubjectObject(Mention m1, Mention m2) {
    if(m1.sentNum != m2.sentNum) return false;
    if(m1.dependingVerb==null || m2.dependingVerb ==null) return false;
    if (m1.dependingVerb == m2.dependingVerb
         && ((m1.isSubject && (m2.isDirectObject || m2.isIndirectObject || m2.isPrepositionObject))
              || (m2.isSubject && (m1.isDirectObject || m1.isIndirectObject || m1.isPrepositionObject)))) {
      return true;
    }
    return false;
  }
}
