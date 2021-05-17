#!/bin/bash

function func_help {
echo -n "\
./doctool.sh COMMAND ARGS
--COMMANDS--
buildhtml
Build HTML from TeX

updatetranslation
Extract strings and create xliff translation file

mergetranslation
Merge translation from xliff to TeX

help
Show this help and exit

checkdeps
Run dependency checker

--Dependencies--
・pandoc(Tested with 2.13-1)
https://github.com/jgm/pandoc
https://pandoc.org/

・pandoc-crossref(Tested with v0.3.10.0a)
https://github.com/lierdakil/pandoc-crossref
https://lierdakil.github.io/pandoc-crossref/

・Python(Tested with 3.8.5)
https://www.python.org/

・GNU Sed(Tested with 4.7,May not work with Posix SED)
https://www.gnu.org/software/sed/

・Bash(Tested with 5.0.17,Not work with sh,Untested with zsh)
https://www.gnu.org/software/bash/

・Awk(Tested with 5.0.1-API2.0)

・GNU grep（Tested with 3.4)

・xmllint(Tested with 20910)

・Perl(Tested with 5-v30)
"
}


function func_checkdeps {

echo Warning:This checker doesnt check version.

which pandoc && echo Pass || echo -n "\
Pandoc not found!

-Solution-
Please install pandoc above v2.13.
Download releases from here and install it.(maybe your package manager have pandoc package,but it will cause bug due to it is outdated)
https://github.com/jgm/pandoc/releases/tag/2.13
"

which pandoc-crossref && echo Pass || { ls ./pandoc-crossref && echo Pass || echo -n "\
pandoc-crossref not found!

-Solution1-
Download file from here
https://github.com/lierdakil/pandoc-crossref/releases
Extract it
Put files to working directory(THIS DIRECTORY)

-Solution2-
Follow this guide to install pandoc-crossref in to system
https://github.com/lierdakil/pandoc-crossref/blob/master/README.md
"; }

which python && echo Pass || echo -n "\
Python not found!

-Solution-
Just run 
apt install python
"

which xmllint && echo Pass || echo -n "\
xmllint not found!

-Solution-
Just run 
apt install xmllint
"

which perl && echo Pass || echo -n "\
Perl not found!

-Solution-
Just run 
apt install perl
"
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
#sed -i -e "s/\<img src\=\"images\/icon.png\" style\=\"width:2cm\"/\<img src\=\"images\/icon-.png\" style\=\"width\:16.69\%\"/g" $OUTPUT 
sed -i -r -e "s/(<img src\=\"images\/icon\.png\" style\=\")width\:2cm(\" alt\=\"image\")/\1width:16.69%\2/g" $OUTPUT

##Hide Level5 section number
#sed -i -e "s/<span class=\"header-section-number\">.*\..*\..*\..*\..*<\/span\>/<span class=\"header-section-number\" style=\"display\: none\;\"><\/span>/g" $OUTPUT
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
        string_title=$(echo ${line_title} | grep -oP "((?<=section{)|(?<=subsection{)|(?<=subsubsection{)|(?<=chapter{)|(?<=caption{)).*?(?=})")
        echo "<string name=\"${stringkey_title}\">${string_title}</string>" >>${OUTPUT}
        echo -e "--\n$stringkey_title\n$string_title\n--\n"

    done < <(grep -P "(?<=\%\%##).*(?=>>)" ${file})

    while read stringkey_content
    do

        string_content=$(sed '1,/%%!!'${stringkey_content}'<</d;/%%!!>>/,$d' ${file})
        echo "<string name=\"${stringkey_content}\">${string_content}</string>" >>${OUTPUT}
        echo -e "--\n$stringkey_content\n$string_content\n--\n"

    done < <(grep -oP "(?<=\%\%!!).*(?=<<)" ${file})

done < <(find ./ -type f -name "*.tex")
sed -i -e '1i <?xml version="1.0" encoding="utf-8"?>' \
       -e '1i <resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">' \
       -e '$a </resources>' ${OUTPUT}
}

function func_merge-translation {
INPUT=strings.xml
keys=$(grep -oP "(?<=<string name=\").*?(?=\">)" ${INPUT})

while read key_content
do

    string_content=$(echo 'cat resources/string[@name="'${key_content}'"]/text()' | xmllint --shell ${INPUT} | sed -e '$d' -e '1d'|sed 's/\\/\\\\/g')
    while read file
    do
        source=$(cat ${file})
        echo "import re;import sys;print(re.sub(r'(?<=%%!!"${key_content}"<<\n)[^%%!!>>]*(?=%%!!>>)', sys.argv[2]+'\n', sys.argv[1], flags=re.M))" | python - "${source}" "${string_content}" >${file}

    done < <(find ./ -type f -name "*.tex")

done < <(echo "$keys" | grep -Pv ".*(?===title)")


while read key_title
do
    string_title=$(echo 'cat resources/string[@name="'${key_title}'"]/text()' | xmllint --shell ${INPUT} | sed -e '$d' -e '1d'|sed 's/\\/\\\\/g')

    while read file
    do

         perl -pi -e "s/(section\{|subsection\{|subsubsection\{|chapter\{|caption\{).*?(\}.*\%\%\#\#${key_title}>>)/\1${string_title}\2/" ${file}

    done < <(find ./ -type f -name "*.tex")
done < <(echo "$keys" | grep -P ".*(?===title)")

}

case $1 in
"help" ) func_help ;;
"buildhtml" ) func_build-html ;;
"updatetranslation" ) func_update-xliff ;;
"mergetranslation" ) func_merge-translation ;;
"checkdeps" ) func_checkdeps ;;
esac
