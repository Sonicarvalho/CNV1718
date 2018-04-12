#!/bin/bash
i=0
while IFS='' read -r line || [[ -n "$line" ]]; do
	var=$((var + 1))
	echo "::test $var::"
	url="http://localhost:8000/test?$line"
	strip=${url%$'\r'}
	
	curl $strip >> tests_marc.txt
	echo $'\n' >> tests_marc.txt
done < "$1"
