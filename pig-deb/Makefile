default_target: deb
deb: pig.deb

pig.deb:
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums
	cd src && find etc/pig/ -type f -printf '/%p\n' > DEBIAN/conffiles
	dpkg-deb --build  src/ pig.deb

clean: 
	rm pig.deb
