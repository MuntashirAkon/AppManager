#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later

 { [[ $(uname) = Darwin ]] || [[ $(uname) =~ .*BSD.* ]]; } && { alias sed="gsed" ; alias grep="ggrep" ; alias awk="gawk"; }
#cd $0

function func_checkdeps {

echo Warning:This checker doesnt check version.

which pandoc && echo Pass || echo -n "Pandoc not found!"

{ which pandoc-crossref  ||  ls ./pandoc-crossref; } && echo Pass || echo -n "pandoc-crossref not found!"

which python && echo Pass || echo -n "Python not found!"

which xmllint && echo Pass || echo -n "xmllint not found!"

which perl && echo Pass || echo -n "Perl not found!"
}

function func_build-html {

OUTPUT=main.pandoc.html
pandoc main.tex -c main.css -c custom.css -o $OUTPUT -t html5 -f latex -s --toc -N --section-divs --default-image-extension=png -i -F pandoc-crossref --citeproc --verbose

##Custom Color Fixup
while read line
do
colorset=$(echo $line | sed -e "s/\\definecolor{//" -e "s/}{HTML}{/ /" -e "s/}//")
colorname=$(echo $colorset | awk '{print $1}')
colorcode=$(echo $colorset | awk '{print $2}' | sed -e "s/^/#/")
sed -i -e "s/style\=\"background-color\: ${colorname}\"/style\=\"background-color\: ${colorcode}\"/g" \
       -e "s/style\=\"color\: ${colorname}\"/style\=\"color\: ${colorcode}\"/g" $OUTPUT
done < <(grep "\definecolor" main.tex)


##Add link on section title
while read line
do
sectionid=$(echo $line | grep -oP "(?<=\<span class\=\"toc-section-number\"\>).*(?=<\/span\>)")
tocsectionid=toc:${sectionid}
sed -i -r "s/<span class=\"toc-section-number\">${sectionid}<\/span>/<span class\=\"toc-section-number\" id\=\"${tocsectionid}\"\>${sectionid}<\/span\>/g" $OUTPUT
sed -i -r "s/(<(.*) data-number=\"${sectionid}\"><span class=\"header-section-number\">${sectionid}<\/span>.*<\/\2>)/<a href=\"#${tocsectionid}\">\1<\/a>/" $OUTPUT
done < <(grep -o "<span class=\"toc-section-number\">.*<\/span>" $OUTPUT)


##Icon Fixup(Need --default-image-extension=png option)
sed -i -r -e "s/(<img src\=\"images\/icon\.png\" style\=\")width\:2cm(\" alt\=\"image\")/\1width:16.69%\2/g" $OUTPUT

##Hide Level5 section number
sed -i -r -e "s/(<span class=\"header-section-number\")(>[0-9]*\.[0-9]*\.[0-9]*\.[0-9]*\.[0-9]*<\/span>)/\1 style=\"display:none\;\"\2/g" $OUTPUT

##Alertbox Fixup
sed -i -r "s/<div class\=\"amalert--(.*)\">/<div class\=\"amalert\" style\=\"border\: 3.0pt solid #\1\;\">/g" $OUTPUT


}

function func_update-xliff {
OUTPUT=strings.xml
rm $OUTPUT

while read file
do

    while read line_title
    do

        stringkey_title=$(echo ${line_title} | grep -oP "(?<=\%\%##).*(?=>>)")
        string_title=$(echo ${line_title} | grep -oP "((?<=section{)|(?<=subsection{)|(?<=subsubsection{)|(?<=chapter{)|(?<=caption{)|(?<=paragraph{)).*?(?=})")
        echo "<string name=\"${stringkey_title}\"><![CDATA[${string_title}]]></string>" >>${OUTPUT}
        #echo -e "--\n$stringkey_title\n$string_title\n--\n"

    done < <(grep -P "(?<=\%\%##).*(?=>>)" ${file} | sed -e 's/\\/\\\\/g')

    while read stringkey_content
    do

        string_content=$(sed '1,/%%!!'${stringkey_content}'<</d;/%%!!>>/,$d' ${file})
        echo "<string name=\"${stringkey_content}\"><![CDATA[${string_content}]]></string>" >>${OUTPUT}
        #echo -e "--\n$stringkey_content\n$string_content\n--\n"

    done < <(grep -oP "(?<=\%\%!!).*(?=<<)" ${file})

done < <(find ./ -type f -name "*.tex")
sed -i -e '1i <?xml version="1.0" encoding="utf-8"?>' \
       -e '1i <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">' \
       -e '$a </resources>' ${OUTPUT}
}

function func_merge-translation {
INPUT=strings.xml
OUTPUTDIR=../latextranslated
keys=$(grep -oP "(?<=<string name=\").*?(?=\">)" ${INPUT})

find . | grep -e '\.tex$' -e '\.png$' -e '.png$' -e '.css$' -e main.cfg -e doctool.sh -e Makefile | rsync -R $(cat) ${OUTPUTDIR}

while read key_content
do

    string_content=$(echo 'cat resources/string[@name="'${key_content}'"]/text()' | xmllint --shell "${INPUT}" | sed -e '$d' -e '1d' | sed -e 's/\\/\\\\/g' -e '1s/^<!\[CDATA\[//g' -e '$s/]]>$//g')
    file=$(grep -rl --include="*.tex" "\%\%!!${key_content}<<" ${OUTPUTDIR})
    source=$(cat ${file})

    echo "import re;import sys;print(re.sub(r'(?<=%%!!"${key_content}"<<\n)[^%%!!>>]*(?=%%!!>>)', sys.argv[2]+'\n', sys.argv[1], flags=re.M))" | python - "${source}" "${string_content}" >${file}

done < <(echo "$keys" | grep -Pv ".*(?===title)")


while read key_title
do

    string_title=$(echo 'cat resources/string[@name="'${key_title}'"]/text()' | xmllint  --shell "${INPUT}" | sed -e '$d' -e '1d' | sed -e 's/\\/\\\\/g' -e 's/\//\\\//g' -e '1s/^<!\[CDATA\[//g' -e '$s/]]>$//g')
    file=$(grep -rl --include="*.tex" "\%\%##${key_title}>>" ${OUTPUTDIR})

    perl -i -e "s/(section\{|subsection\{|subsubsection\{|chapter\{|caption\{|paragraph\{).*?(\}.*\%\%\#\#${key_title}>>)/\1${string_title}\2/" ${file}

done < <(echo "$keys" | grep -P ".*(?===title)")

}

function func_detectabuse {
baseDir=./
compareDir=../latextranslated

#Compare number of latex tags

#Check URL changes
baseFiles=$(find $baseDir | grep -e '\.tex$' | sed -e "s%$baseDir%%g" -e "s/^\///g")
compareFiles=$(find $compareDir | grep -e '\.tex$' | sed -e "s%$compareDir%%g" -e "s/^\///g")


while read test
do

 { echo "$compareFiles" | grep "$test" >/dev/null; } && {
   base=$(urlextract $baseDir/$test)
   compare=$(urlextract $compareDir/$test)

   echo "$compare" | grep -vh "$base"
   echo "--"
   echo " "
   echo "WARNING:$baseDir/$test(BASE) does not have these URLs,but $compareDir/$test(COMPARE) has.These links has possibility of spam!"
   echo "--"
   echo " "
 }

done < <(echo "$baseFiles")
}

case $1 in
"buildhtml" ) func_build-html ;;
"updatetranslation" ) func_update-xliff ;;
"mergetranslation" ) func_merge-translation ;;
"checkdeps" ) func_checkdeps ;;
esac
