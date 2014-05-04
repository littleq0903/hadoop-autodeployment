default_target: deb
deb: hive.deb
all: deb

hive.deb: md5sums conffiles
	dpkg-deb --build  src/ hive.deb

md5sums:
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums

conffiles:
	cd src && find etc/hive/ -type f -printf '/%p\n' > DEBIAN/conffiles

clean:
	rm hive.deb
